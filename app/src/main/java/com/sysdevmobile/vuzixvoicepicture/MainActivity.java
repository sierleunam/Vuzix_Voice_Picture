package com.sysdevmobile.vuzixvoicepicture;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.sysdevmobile.vuzixvoicepicture.FileUtils.deleteFilename;
import static com.sysdevmobile.vuzixvoicepicture.FileUtils.getPublicDownloadsStorageFile;
import static com.sysdevmobile.vuzixvoicepicture.FileUtils.writeTextToFile;

public class MainActivity extends Activity implements RotationListener.rotationCallbackFn {

    public static final String DOWNLOADS_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
    public static final String LOG_TAG = "VoiceSample";
    public static final String CUSTOM_SDK_INTENT = "com.sysdevmobile.vuzixvoicepicture.CustomIntent";
    public static final int FLASH_OFF = 2001;
    public static final int FLASH_ON = 2002;
    public static final int FLASH_AUTO = 2003;
    public static final int FLASH_TORCH = 2004;
    static final String IMAGE_FILENAME = "image.filename";
    private static final String TAG = "CameraFlash_App";
    private static final long PREVIEW_TIME_MILLISECS = 2000;
    private final static int TAKEPICTURE_COMPLETED = 1001;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private final int[] RotationConversion = {  /* ROTATION_0 = 0 */      0,
            /* ROTATION_90 = 1; */   90,
            /* ROTATION_180 = 2; */ 180,
            /* ROTATION_270 = 3; */ 270};
    public boolean mRecognizerActive = true;
    Button mTakingButton;
    VoiceCmdReceiver mVoiceCmdReceiver;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCameraCaptureSessions;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private ImageReader mImageReader;
    private int mFlashMode;
    private boolean mTakingPicture, mSuspending;
    private RotationListener mRotationListener;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundPreviewHandler;
    private HandlerThread mBackgroundPreviewThread;
    private Handler mHandler;


    /**
     * You may prefer using explicit intents for each recognized phrase. This receiver demonstrates that.
     */
    private MyIntentReceiver myIntentReceiver;


    /**
     * when created we setup the layout and the speech recognition
     *
     * @param savedInstanceState ...
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);


        // Create the voice command receiver class
        mVoiceCmdReceiver = new VoiceCmdReceiver(this);

        // Now register another intent handler to demonstrate intents sent from the service
        myIntentReceiver = new MyIntentReceiver();
        registerReceiver(myIntentReceiver, new IntentFilter(CUSTOM_SDK_INTENT));

        // Handle taking the picture. Disable the button while it processes.
        mTakingButton = (Button) findViewById(R.id.btn_takepicture);
        mTakingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickTakePicture();
            }
        });

        // Set up the preview
        mTextureView = (TextureView) findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        // Create a handler to respond when the photo is complete.
        mHandler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message msg) {
                if (msg.what == TAKEPICTURE_COMPLETED) {
                    onPictureComplete();
                } else {
                    super.handleMessage(msg);
                }
            }
        };

        // Create the listener to handle M300 orientation changes
        deleteFilename(IMAGE_FILENAME);
        mRotationListener = new RotationListener();

        // Request the new state
        mVoiceCmdReceiver.TriggerRecognizerToListen(mRecognizerActive);
    }

    /**
     * Unregister from the speech SDK
     */
    @Override
    protected void onDestroy() {
        unregisterReceiver(myIntentReceiver);

        mVoiceCmdReceiver.unregister();
        super.onDestroy();
    }

//    /**
//     * A callback for the SDK to notify us if the recognizer starts or stop listening
//     *
//     * @param isRecognizerActive boolean - true when listening
//     */
//    public void RecognizerChangeCallback(boolean isRecognizerActive) {
//        mRecognizerActive = isRecognizerActive;
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
////                updateListenButtonText();
//            }
//        });
//    }

    public int getmFlashMode() {
        return mFlashMode;
    }

    public void setmFlashMode(int mFlashMode) {
        this.mFlashMode = mFlashMode;
    }

    /**
     * Called every time the user presses the "Take Picture" button. Maintains state of UI.
     */
    public void onClickTakePicture() {
        if (!mTakingPicture) {
            mTakingPicture = true;
            takeStillPicture();
            mTakingButton.setEnabled(false);
        }
    }

    /**
     * Called when processing the picture request completes
     */
    private void onPictureComplete() {
        try {
            Thread.sleep(PREVIEW_TIME_MILLISECS); // Sleep for 3 seconds to allow the user to see the photograph
        } catch (InterruptedException e) {
            // Just discard the exception.  We don't care if we were aborted
        }
        mTakingPicture = false;
        finish();
    }

    /**
     * Utility to start the background threads
     */
    protected synchronized void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mBackgroundPreviewThread = new HandlerThread("Camera Preview");
        mBackgroundPreviewThread.start();
        mBackgroundPreviewHandler = new Handler(mBackgroundPreviewThread.getLooper());
    }

    /**
     * Utility to stop the background threads
     */
    protected synchronized void stopBackgroundThread() {
        mBackgroundPreviewThread.quitSafely();
        mBackgroundThread.quitSafely();

        try {
            mBackgroundPreviewThread.join();
            mBackgroundPreviewThread = null;
            mBackgroundPreviewHandler = null;

            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Take the photograph
     */
    protected void takeStillPicture() {
        if (null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null");
            return;
        }

        switch (getmFlashMode()) {
            case FLASH_ON:
            case FLASH_AUTO:
                // This does some extra work, then falls through to createCameraStillCapture()
                precaptureTrigger();
            case FLASH_OFF:
            case FLASH_TORCH:
                Log.i(TAG, "Capture called from takeStillPicture");
                createCameraStillCapture();
                break;
        }
    }

    /**
     * Utility to get the image rotation in degrees
     *
     * @return int angle. For an Activity properly in sensorLandscape, this is either 0 or 180.
     */
    private int getImageRotationDegrees() {
        // Get one of the 4 integer indexes of a system rotation
        final int systemRotation = ((WindowManager) Objects.requireNonNull(getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getRotation();
        // Convert to corresponding degrees
        return RotationConversion[systemRotation];
    }

    /**
     * Utility to invert the degrees by 180
     *
     * @param degrees int degree value to invert, from 0-359
     * @return int degree value from 0-359 inverted from input by 180 degrees
     */
    private int invertDegrees(int degrees) {
        degrees += 180;
        if (degrees >= 360) {
            degrees -= 360;
        }
        return degrees;
    }

    /**
     * This method is called every time the device changes rotation. Update the preview
     *
     * @param newRotation int Either Surface.ROTATION_0 or Surface.ROTATION_180
     */
    @Override
    public void onRotationChanged(int newRotation) {
        updatePreview();
    }

    /**
     * Create the camera preview
     */
    protected synchronized void createCameraPreview() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if ((null == texture) || (null == mCameraDevice)) {
                return;
            }
            texture.setDefaultBufferSize(640, 360);// preview size
            Surface surface = new Surface(texture);

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCameraCaptureSessions = Objects.requireNonNull(session);
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * This activates the camera.  It includes permissions checks. If successful it then opens the preview.
     */
    private synchronized void openCamera() {
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.i(TAG, "is camera open");
        try {
            // The M300 only has one camera, so the index can be constant
            String mCameraId = mCameraManager.getCameraIdList()[0];
            // Add permission for camera, let user grant the permission.
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            // Open the camera and provide the required callbacks
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.i(TAG, "onOpened");
                    mCameraDevice = camera;
                    setmFlashMode(FLASH_OFF);
                    createCameraPreview();   // Only open the preview if the camera is opened successfully
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    mCameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    if (null != mCameraDevice) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mCameraManager = null;
            mCameraDevice = null;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Utility to set the flash modes and start the preview
     */
    protected void updatePreview() {
        if (null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        try {
            setPreviewFlashMode();
            mTextureView.setRotation(getImageRotationDegrees());
            mCameraCaptureSessions.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Utility to set the flash modes and capture the image.
     * <p>
     * This sets the flash mode, the image orientation, and adds the listener so we know when it is done
     */
    private void capture() {
        try {
            setCaptureFlashMode();
            int degrees = invertDegrees(getImageRotationDegrees());  // The M300 sensor is inverted by reference to the encoding. This straightens it.
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, degrees);
            mCameraCaptureSessions.capture(mCaptureRequestBuilder.build(), null, mBackgroundPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up the camera device with all the required call-backs, and initiates the capture
     */
    private void createCameraStillCapture() {
        try {
            final int imageWidth = 1920;        // image size
            final int imageHeight = 1080;       // image size

            List<Surface> outputSurfaces = new ArrayList<>();
            // Add an output surface to view the result of the photograph in place of the preview
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 360); // preview size
            Surface surface = new Surface(texture);
            outputSurfaces.add(surface);

            // Add an output surface to write the image to a file
            ImageReader reader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.JPEG, 2);
            outputSurfaces.add(reader.getSurface());

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(surface);
            mCaptureRequestBuilder.addTarget(reader.getSurface());


            final String imageFileName = String.format(Locale.getDefault(), "IMAGE%d_%dx%d.jpg", System.currentTimeMillis(), imageWidth, imageHeight);

            // Create a listener that saves the image file and notifies us when complete
            final File file = getPublicDownloadsStorageFile(imageFileName);

            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    try (Image image = reader.acquireLatestImage()) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                        completed();  // Send ourselves a message that we're completed
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    try (OutputStream output = new FileOutputStream(file)) {
                        output.write(bytes);
                    }
                }

                private void completed() {
                    Message msg = mHandler.obtainMessage();
                    msg.what = TAKEPICTURE_COMPLETED;
                    mHandler.sendMessage(msg);
                    writeTextToFile(imageFileName);
                }

            }, mBackgroundHandler);

            // Begin the capture session, and take the photograph
            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSessions = session;
                    capture();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * This sets up the Automatic Exposure to expect the flash. If the flash will be used for the
     * photograph, this configures a pre-trigger to allow the AE algorithms to analyze the light levels
     */
    private void precaptureTrigger() {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        texture.setDefaultBufferSize(640, 360); // preview size
        Surface surface = new Surface(texture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            mCaptureRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {

                        switch (getmFlashMode()) {
                            case FLASH_ON:
                                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                                break;
                            case FLASH_AUTO:
                                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                break;
                        }

                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                        session.capture(mCaptureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

                            }

                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                Integer getResult = result.get(CaptureResult.CONTROL_AE_STATE);

                                if (getResult != null) {
                                    switch (getResult) {
                                        case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                                        case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                                        case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                                        case CaptureResult.CONTROL_AE_STATE_LOCKED:
                                            Log.i(TAG, "Capture called from precaptureTrigger. AE_STATE: " + getResult);
                                            createCameraStillCapture();
                                    }
                                }
                            }
                        }, mBackgroundHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Utility to translate our desired flash mode to the required API for the capture
     */
    private void setCaptureFlashMode() {
        switch (getmFlashMode()) {
            case FLASH_OFF:
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case FLASH_TORCH:
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                break;
        }
    }

    /**
     * Utility to translate our desired flash mode to the required API for the preview
     */
    private void setPreviewFlashMode() {
        switch (getmFlashMode()) {
            case FLASH_OFF:
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case FLASH_TORCH:
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                break;
            case FLASH_AUTO:
            case FLASH_ON:
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                break;
        }
    }

    /**
     * Utility function to close the camera
     */
    private synchronized void closeCamera() {
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
        mCameraManager = null;
    }

    /**
     * When we resume, start the worker thread.  Re-open the camera if we previously closed it.
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        mRotationListener.listen(this, this);
        startBackgroundThread();
        if (mSuspending)
            openCamera();
        mSuspending = false;
    }

    /**
     * When we pause, close the camera, stop the worker thread, and remember we've done so
     */
    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        mRotationListener.stop();
        mSuspending = true;
        super.onPause();
    }

    /**
     * Required interface for any activity that requests a run-time permission
     *
     * @param requestCode  int: The request code passed in requestPermissions(android.app.Activity, String[], int)
     * @param permissions  String: The requested permissions. Never null.
     * @param grantResults int: The grant results for the corresponding permissions which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     * @see <a href="https://developer.android.com/training/permissions/requesting.html">https://developer.android.com/training/permissions/requesting.html</a>
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            // Since we only request one "dangerous" permission, we don't need to worry about larger array sizes
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, getResources().getText(R.string.permissions_not_granted), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * Handler called upon the user selecting a flash mode using the options menu
     * <p>
     * This simply stores the selected mode in a member variable
     *
     * @param item MenuItem: The menu item that was selected.
     * @return true if the item was consumed
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.flashoff:
                setmFlashMode(FLASH_OFF);
                break;
            case R.id.flashon:
                setmFlashMode(FLASH_ON);
                break;
            case R.id.flashauto:
                setmFlashMode(FLASH_AUTO);
                break;
            case R.id.flashtorch:
                setmFlashMode(FLASH_TORCH);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        createCameraPreview();
        return true;
    }

    /**
     * Create an options menu
     *
     * @param menu Menu: The options menu in which you place your items.
     * @return true so the menu is shown
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Helper to show a toast
     *
     * @param iStr String message to place in toast
     */
    void popupToast(String iStr) {
        Toast myToast = Toast.makeText(MainActivity.this, iStr, Toast.LENGTH_LONG);
        myToast.show();
    }

    /**
     * Utility to get the name of the current method for logging
     *
     * @return String name of the current method
     */
    public String getMethodName() {
        return LOG_TAG + ":" + this.getClass().getSimpleName() + "." + new Throwable().getStackTrace()[1].getMethodName();
    }

}
