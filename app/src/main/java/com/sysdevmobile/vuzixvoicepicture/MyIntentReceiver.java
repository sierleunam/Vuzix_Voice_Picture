package com.sysdevmobile.vuzixvoicepicture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.Objects;

import static com.sysdevmobile.vuzixvoicepicture.MainActivity.LOG_TAG;

public class MyIntentReceiver extends BroadcastReceiver {

    private static final String ACTION_KEY = "action";
    private MainActivity mMainActivity;

    public MyIntentReceiver(MainActivity iActivity) {
        mMainActivity = iActivity;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(LOG_TAG, "On Receive (Custom Intent)");
        if (Objects.equals(intent.getAction(), MainActivity.CUSTOM_SDK_INTENT)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                // We will determine what type of message this is based upon the extras provided
                if (extras.containsKey(ACTION_KEY)) {

                    boolean act = intent.getBooleanExtra(ACTION_KEY, false);
//                    Toast.makeText(context, "Custom Intent Detected, value: "+act, Toast.LENGTH_LONG).show();
                    if (!act) {
                        mMainActivity.finish();
                    } else
                        mMainActivity.onClickTakePicture();

                }
            } else
                Toast.makeText(context, "Custom Intent Detected (no extras)", Toast.LENGTH_LONG).show();

        }

    }
}
