package com.beetle.face;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.beetle.NativeWebRtcContextRegistry;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.HistoryDB;
import com.beetle.im.IMService;

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
        im.setHost(Config.HOST);
        im.setPort(Config.PORT);

        //already login
        if (Token.getInstance().uid > 0) {
            im.setUid(Token.getInstance().uid);
            im.start();
        }

        registerBroadcastReceiver();
    }

    public void registerBroadcastReceiver() {
        this.registerReceiver(networdStateReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
    }


    public void unregisterBroadcastReceiver() {
        this.unregisterReceiver(networdStateReceiver);
    }


    BroadcastReceiver networdStateReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            if (FaceApplication.this.isNetworkConnected(context)) {
                if (Token.getInstance().uid > 0) {
                    Log.i(TAG, "network connected start im service");
                    IMService.getInstance().stop();
                    IMService.getInstance().start();
                }
            } else {
                Log.i(TAG, "network disconnected stop im service");
                IMService.getInstance().stop();
            }
        }
    };


    public boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }
}
