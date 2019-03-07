package com.sysdevmobile.vuzixvoicepicture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.vuzix.sdk.speechrecognitionservice.VuzixSpeechClient;

import java.util.Objects;

import static android.view.KeyEvent.KEYCODE_ENTER;
import static com.sysdevmobile.vuzixvoicepicture.MainActivity.CUSTOM_SDK_INTENT;
import static com.sysdevmobile.vuzixvoicepicture.MainActivity.LOG_TAG;


/**
 * Class to encapsulate all voice commands
 */
public class VoiceCmdReceiver extends BroadcastReceiver {
    // Voice command substitutions. These substitutions are returned when phrases are recognized.
    // This is done by registering a phrase with a substition. This eliminates localization issues
    // and is encouraged

    private static final String MATCH_TAKE_PICTURE = "take_picture";
    private static final String MATCH_CANCEL = "cancel";
    private static final String MATCH_HELP = "help";
    private static final String MATCH_OKAY = "okay";


    // Voice command custom intent names
    final String TOAST_EVENT = "other_toast";

    private MainActivity mMainActivity;

    /**
     * Constructor which takes care of all speech recognizer registration
     *
     * @param iActivity MainActivity from which we are created
     */
    public VoiceCmdReceiver(MainActivity iActivity) {
        mMainActivity = iActivity;
        mMainActivity.registerReceiver(this, new IntentFilter(VuzixSpeechClient.ACTION_VOICE_COMMAND));
        Log.d(LOG_TAG, "Connecting to M300 SDK");

        try {
            // Create a VuzixSpeechClient from the SDK
            VuzixSpeechClient sc = new VuzixSpeechClient(iActivity);
            // Delete specific phrases. This is useful if there are some that sound similar to yours, but
            // you want to keep the majority of them intact
            //sc.deletePhrase("torch on");
            //sc.deletePhrase("torch on");
            sc.deletePhrase("okay");
            sc.deletePhrase("pick this");
            sc.deletePhrase("cancel");
            sc.deletePhrase("confirm");
            sc.deletePhrase("go back");


            // Delete every phrase in the dictionary! (Available in SDK version 3)
//            sc.deletePhrase("*");


            sc.insertKeycodePhrase("enter", KEYCODE_ENTER);

            // Insert a custom intent.  Note: these are sent with sendBroadcastAsUser() from the service
            // If you are sending an event to another activity, be sure to test it from the adb shell
            // using: am broadcast -a "<your intent string>"
            // This example sends it to ourself, and we are sure we are active and registered for it
            Intent customToastIntent = new Intent(CUSTOM_SDK_INTENT);
            sc.defineIntent(TOAST_EVENT, customToastIntent);
            sc.insertIntentPhrase("canned toast", TOAST_EVENT);

            // Insert phrases for our broadcast handler
            //
            // ** NOTE **
            // The "s:" is required in the SDK version 2, but is not required in the latest JAR distribution
            // or SDK version 3.  But it is harmless when not required. It indicates that the recognizer is making a
            // substitution.  When the multi-word string is matched (in any language) the associated MATCH string
            // will be sent to the BroadcastReceiver

            sc.insertPhrase(mMainActivity.getResources().getString(R.string.takepicture), MATCH_TAKE_PICTURE);
            sc.insertPhrase(mMainActivity.getResources().getString(R.string.ok), MATCH_TAKE_PICTURE);
            sc.insertPhrase(mMainActivity.getResources().getString(R.string.cancel), MATCH_CANCEL);
            sc.insertPhrase(mMainActivity.getResources().getString(R.string.help), MATCH_HELP);




            // See what we've done
            Log.i(LOG_TAG, sc.dump());

            // The recognizer may not yet be enabled in Settings. We can enable this directly
            VuzixSpeechClient.EnableRecognizer(mMainActivity, true);
        } catch (NoClassDefFoundError e) {
            // We get this exception if the SDK stubs against which we compiled cannot be resolved
            // at runtime. This occurs if the code is not being run on an M300 supporting the voice
            // SDK
            Toast.makeText(iActivity, R.string.only_on_m300, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, iActivity.getResources().getString(R.string.only_on_m300));
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
            iActivity.finish();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error setting custom vocabulary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * All custom phrases registered with insertPhrase() are handled here.
     * <p>
     * Custom intents may also be directed here, but this example does not demonstrate this.
     * <p>
     * Keycodes are never handled via this interface
     *
     * @param context Context in which the phrase is handled
     * @param intent  Intent associated with the recognized phrase
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        // All phrases registered with insertPhrase() match ACTION_VOICE_COMMAND as do
        // recognizer status updates
        if (Objects.equals(intent.getAction(), VuzixSpeechClient.ACTION_VOICE_COMMAND)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                // We will determine what type of message this is based upon the extras provided
                if (extras.containsKey(VuzixSpeechClient.PHRASE_STRING_EXTRA)) {
                    // If we get a phrase string extra, this was a recognized spoken phrase.
                    // The extra will contain the text that was recognized, unless a substitution
                    // was provided.  All phrases in this example have substitutions as it is
                    // considered best practice
                    String phrase = intent.getStringExtra(VuzixSpeechClient.PHRASE_STRING_EXTRA);
                    Log.e(LOG_TAG, mMainActivity.getMethodName() + " \"" + phrase + "\"");
                    // Determine the specific phrase that was recognized and act accordingly
                    switch (phrase) {
                        case MATCH_TAKE_PICTURE:
                        case MATCH_OKAY:
                            mMainActivity.onClickTakePicture();
                            break;
                        case MATCH_CANCEL:

                            mMainActivity.finish();

                            break;
                        case MATCH_HELP:
                            mMainActivity.popupToast("Available commands: OK, Take Picture, Cancel");
                            break;
                        default:
                            Log.e(LOG_TAG, "Phrase not handled");
                            mMainActivity.popupToast("Phrase not handled: " + phrase);
                            break;
                    }
                } else if (extras.containsKey(VuzixSpeechClient.RECOGNIZER_ACTIVE_BOOL_EXTRA)) {
                    // if we get a recognizer active bool extra, it means the recognizer was
                    // activated or stopped
//                    boolean isRecognizerActive = extras.getBoolean(VuzixSpeechClient.RECOGNIZER_ACTIVE_BOOL_EXTRA, false);
                    boolean isRecognizerActive = extras.getBoolean(VuzixSpeechClient.RECOGNIZER_ACTIVE_BOOL_EXTRA, false);
                    if(!isRecognizerActive)
                    {
//
                        mMainActivity.mRecognizerActive = !mMainActivity.mRecognizerActive;
                        // Manually calling this syncrhonizes our UI state to the recognizer state in case we're
                        // requesting the current state, in which we won't be notified of a change.
//                        mMainActivity.updateListenButtonText();
                        // Request the new state
                        TriggerRecognizerToListen(true);
//                        mMainActivity.OnListenClick();
                    }
//                    mMainActivity.RecognizerChangeCallback(isRecognizerActive);
                }
            }
        }
    }

    /**
     * Called to unregister for voice commands. An important cleanup step.
     */
    public void unregister() {
        try {
            mMainActivity.unregisterReceiver(this);
            Log.i(LOG_TAG, "Custom vocab removed");
            mMainActivity = null;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Custom vocab died " + e.getMessage());
        }
    }

    /**
     * Handler called when "Listen" button is clicked. Activates the speech recognizer identically to
     * saying "Hello Vuzix"
     *
     * @param bOnOrOff boolean True to enable listening, false to cancel it
     */
    public void TriggerRecognizerToListen(boolean bOnOrOff) {
        try {
            VuzixSpeechClient.TriggerVoiceAudio(mMainActivity, bOnOrOff);
        } catch (NoClassDefFoundError e) {
            // The voice SDK was added in version 2. The constructor will have failed if the
            // target device is not an M300 that is compatible with SDK version 2.  But the trigger
            // command with the bool was added in SDK version 4.  It is possible the M300 does not
            // yet have the TriggerVoiceAudio interface. If so, we get this exception.
            Toast.makeText(mMainActivity, R.string.upgrade_m300, Toast.LENGTH_LONG).show();
        }
    }

}
