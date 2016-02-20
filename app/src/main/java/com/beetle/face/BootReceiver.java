package com.beetle.face;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class BootReceiver extends BroadcastReceiver {
    public static final String TAG = "face";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction().toString();
        Log.i(TAG, "broadcast action:" + action);
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i(TAG, "boot completed");
            return;
        }
    }
}
