package com.beetle.face;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.util.Log;

import com.beetle.NativeWebRtcContextRegistry;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.HistoryDB;
import com.beetle.voip.IMService;

import com.google.code.p.leveldb.LevelDB;

import java.io.File;

/**
 * Created by houxh on 14-12-31.
 */
public class FaceApplication  extends Application {
    private final static String TAG = "face";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "app application create");

        LevelDB ldb = LevelDB.getDefaultDB();
        String dir = getFilesDir().getAbsoluteFile() + File.separator + "db";
        Log.i(TAG, "dir:" + dir);
        ldb.open(dir);

        ContactDB cdb = ContactDB.getInstance();
        cdb.setContentResolver(getApplicationContext().getContentResolver());
        cdb.monitorConctat(getApplicationContext());

        HistoryDB.initDatabase(getApplicationContext());

        new NativeWebRtcContextRegistry().register(this);

        IMService im =  IMService.getInstance();
        String androidID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        im.setDeviceID(androidID);
        im.registerConnectivityChangeReceiver(getApplicationContext());
        //already login
        if (Token.getInstance().uid > 0) {
            im.setToken(Token.getInstance().accessToken);
            im.start();
        }
    }
}
