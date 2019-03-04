package com.sysdevmobile.vuzixvoicepicture;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;
import android.view.WindowManager;

public class RotationListener {
    /**
     * Interface for receiving callbacks from this listener class
     */
    public interface rotationCallbackFn {
        /**
         * Method that is called when the rotation of the M300 changes
         * @param newRotation int Either Surface.ROTATION_0 or Surface.ROTATION_180
         */
        void onRotationChanged(int newRotation);
    }

    private int lastRotation;
    private WindowManager mWindowManager;
    private OrientationEventListener mOrientationEventListener;

    private rotationCallbackFn mCallback;


    /**
     * Register a listener
     * @param context Context of your activity
     * @param callback rotationCallbackFn to be called when the rotation changes
     */
    public void listen(Context context, rotationCallbackFn callback) {
        // registering the listening only once.
        stop();
        mCallback = callback;
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        // set to a rate suitable for screen orientation changes
        mOrientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                onOrientationChangedHandler();
            }
        };
        mOrientationEventListener.enable();
        lastRotation = mWindowManager.getDefaultDisplay().getRotation();
    }

    /**
     * Handles the rotation change. Called for every degree.  Only calls the callback if it is significant
     */
    private void onOrientationChangedHandler() {
        if( mWindowManager != null && mCallback != null) {
            int newRotation = mWindowManager.getDefaultDisplay().getRotation();
            if (newRotation != lastRotation) {
                mCallback.onRotationChanged(newRotation);
                lastRotation = newRotation;
            }
        }
    }

    /**
     * Stop receiving rotation callbacks.  Call from your onPause()
     */
    public void stop() {
        if(mOrientationEventListener != null) {
            mOrientationEventListener.disable();
        }
        mOrientationEventListener = null;
        mWindowManager = null;
        mCallback = null;
    }
}
