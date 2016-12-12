package com.beetle.face;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.beetle.face.activity.ConferenceActivity;
import com.beetle.face.activity.ConferenceCreatorActivity;
import com.beetle.face.activity.VOIPVideoActivity;
import com.beetle.face.activity.VOIPVoiceActivity;
import com.beetle.face.api.IMHttpFactory;
import com.beetle.face.api.body.PostDeviceToken;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.model.SyncKeyHandler;
import com.beetle.face.tools.event.BusProvider;
import com.beetle.face.tools.event.DeviceTokenEvent;
import com.beetle.face.tools.event.LoginSuccessEvent;
import com.beetle.im.IMService;
import com.beetle.im.SystemMessageObserver;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;
import com.beetle.voip.VOIPCommand;

import com.google.code.p.leveldb.LevelDB;
import com.huawei.android.pushagent.api.PushManager;
import com.squareup.otto.Subscribe;
import com.xiaomi.mipush.sdk.MiPushClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by houxh on 14-12-31.
 */
public class FaceApplication  extends Application implements VOIPObserver, SystemMessageObserver {
    private final static String TAG = "face";

    @Override
    public void onCreate() {
        super.onCreate();

        if (!isAppProcess()) {
            Log.i(TAG, "service application create");
            return;
        }

        Log.i(TAG, "app application create");


        LevelDB ldb = LevelDB.getDefaultDB();
        String dir = getFilesDir().getAbsoluteFile() + File.separator + "db";
        Log.i(TAG, "dir:" + dir);
        ldb.open(dir);

        ContactDB cdb = ContactDB.getInstance();
        cdb.setContentResolver(getApplicationContext().getContentResolver());
        cdb.monitorConctat(getApplicationContext());

        HistoryDB.initDatabase(getApplicationContext());

        IMService im =  IMService.getInstance();
        im.setHost(Config.SDK_HOST);

        String androidID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        im.setDeviceID(androidID);
        im.registerConnectivityChangeReceiver(getApplicationContext());

        im.pushVOIPObserver(this);
        im.addSystemObserver(this);

        BusProvider.getInstance().register(this);

        //already login
        if (Token.getInstance().uid > 0) {
            startIMService();

            if (isHuaweiDevice()) {
                initHuaweiPush();
            } else {
                initXiaomiPush();
            }
        }
    }

    void startIMService() {
        IMService im = IMService.getInstance();
        im.setToken(Token.getInstance().accessToken);
        im.setUID(Token.getInstance().uid);

        SyncKeyHandler handler = new SyncKeyHandler(this.getApplicationContext(), "sync_key");
        handler.load();

        HashMap<Long, Long> groupSyncKeys = handler.getSuperGroupSyncKeys();
        IMService.getInstance().clearSuperGroupSyncKeys();
        for (Map.Entry<Long, Long> e : groupSyncKeys.entrySet()) {
            IMService.getInstance().addSuperGroupSyncKey(e.getKey(), e.getValue());
            Log.i(TAG, "group id:" + e.getKey() + "sync key:" + e.getValue());
        }
        IMService.getInstance().setSyncKey(handler.getSyncKey());
        Log.i(TAG, "sync key:" + handler.getSyncKey());
        im.setSyncKeyHandler(handler);

        im.start();
    }

    private boolean isAppProcess() {
        Context context = getApplicationContext();
        int pid = android.os.Process.myPid();
        Log.i(TAG, "pid:" + pid + "package name:" + context.getPackageName());
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            Log.i(TAG, "package name:" + appProcess.processName + " importance:" + appProcess.importance + " pid:" + appProcess.pid);
            if (pid == appProcess.pid) {
                if (appProcess.processName.equals(context.getPackageName())) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    @Subscribe
    public void onLoginSuccess(LoginSuccessEvent event) {
        Log.i(TAG, "application on login success");
        IMService im =  IMService.getInstance();
        im.setToken(Token.getInstance().accessToken);
        im.start();
        im.pushVOIPObserver(this);
        if (isHuaweiDevice()) {
            initHuaweiPush();
        } else {
            initXiaomiPush();
        }
    }

    @Subscribe
    public void onDeviceToken(DeviceTokenEvent event) {
        if (event.hwDeviceToken != null) {
            Log.i(TAG, "hw device token:" + event.hwDeviceToken);
            bindWithHuawei(event.hwDeviceToken);
        }
        if (event.xmDeviceToken != null) {
            Log.i(TAG, "xiaomi device token:" + event.xmDeviceToken);
            bindWithXiaomi(event.xmDeviceToken);
        }
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

    private int getNow() {
        Date now = new Date();
        return (int)(now.getTime()/1000);
    }

    @Override
    public void onSystemMessage(String sm) {
        try {
            Log.i(TAG, "system message:" + sm);
            int now = getNow();
            JSONObject obj = new JSONObject(sm);
            if (!obj.has("conference")) {
                return;
            }
            JSONObject j = obj.getJSONObject("conference");
            int ts = j.getInt("timestamp");
            long initiator = j.getLong("initiator");
            //50s内发起的呼叫
            if (now - ts > -10 && now - ts < 50 && initiator != Token.getInstance().uid) {
                long conferenceID = j.getLong("id");
                Intent intent = new Intent(this, ConferenceActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("is_initiator", false);
                intent.putExtra("conference_id", (long)conferenceID);
                startActivity(intent);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private boolean isHuaweiDevice() {
        String os = Build.HOST;
        return !TextUtils.isEmpty(os) && os.toLowerCase().contains("huawei");
    }


    private void initXiaomiPush() {
        // 注册push服务，注册成功后会向XiaomiPushReceiver发送广播
        // 可以从onCommandResult方法中MiPushCommandMessage对象参数中获取注册信息
        String appId = "2882303761517437969";
        String appKey = "5171743793969";
        MiPushClient.registerPush(this, appId, appKey);
    }

    private void initHuaweiPush() {
        PushManager.requestToken(this);
    }

    private void bindWithHuawei(String token) {
        PostDeviceToken postDeviceToken = new PostDeviceToken();
        postDeviceToken.hwDeviceToken = token;
        bindDeviceTokenToIM(postDeviceToken);
    }

    private void bindWithXiaomi(String token) {
        PostDeviceToken postDeviceToken = new PostDeviceToken();
        postDeviceToken.xmDeviceToken = token;
        bindDeviceTokenToIM(postDeviceToken);
    }

    private void bindDeviceTokenToIM(PostDeviceToken postDeviceToken) {
        IMHttpFactory.SDKSingleton().bindDeviceToken(postDeviceToken)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object obj) {
                        Log.i("im", "bind success");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i("im", "bind fail");
                    }
                });
    }

}
