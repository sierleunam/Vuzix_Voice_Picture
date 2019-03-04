package com.sysdevmobile.vuzixvoicepicture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import static com.sysdevmobile.vuzixvoicepicture.MainActivity.LOG_TAG;

public class MyIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(LOG_TAG, "On Receive");
        Toast.makeText(context, "Custom Intent Detected", Toast.LENGTH_LONG).show();
    }
}
