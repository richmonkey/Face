package com.beetle.face;

import android.app.Application;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.beetle.NativeWebRtcContextRegistry;
import com.beetle.face.activity.VOIPVideoActivity;
import com.beetle.face.activity.VOIPVoiceActivity;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.tools.event.BusProvider;
import com.beetle.face.tools.event.LoginSuccessEvent;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;
import com.beetle.voip.VOIPCommand;
import com.beetle.voip.VOIPService;

import com.google.code.p.leveldb.LevelDB;
import com.squareup.otto.Subscribe;

import java.io.File;

/**
 * Created by houxh on 14-12-31.
 */
public class FaceApplication  extends Application implements VOIPObserver {
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

        VOIPService im =  VOIPService.getInstance();
        im.setHost(Config.SDK_HOST);

        String androidID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        im.setDeviceID(androidID);
        im.registerConnectivityChangeReceiver(getApplicationContext());
        //already login
        if (Token.getInstance().uid > 0) {
            im.setToken(Token.getInstance().accessToken);
            im.start();
            im.pushVOIPObserver(this);
        }

        BusProvider.getInstance().register(this);
    }

    @Subscribe
    public void onLoginSuccess(LoginSuccessEvent event) {
        Log.i(TAG, "application on login success");
        VOIPService im =  VOIPService.getInstance();
        im.setToken(Token.getInstance().accessToken);
        im.start();
        im.pushVOIPObserver(this);
    }

    @Override
    public void onVOIPControl(VOIPControl ctl) {
        VOIPState state = VOIPState.getInstance();

        VOIPCommand command = new VOIPCommand(ctl.content);
        Log.i(TAG, "voip state:" + state.state + " command:" + command.cmd);
        if (state.state == VOIPState.VOIP_WAITING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL) {
                Intent intent = new Intent(this, VOIPVoiceActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("peer_uid", ctl.sender);
                intent.putExtra("is_caller", false);
                state.state = VOIPState.VOIP_TALKING;
                startActivity(intent);
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
                Intent intent = new Intent(this, VOIPVideoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("peer_uid", ctl.sender);
                intent.putExtra("is_caller", false);
                state.state = VOIPState.VOIP_TALKING;
                startActivity(intent);
            }
        }
    }
}
