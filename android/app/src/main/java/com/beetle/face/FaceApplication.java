package com.beetle.face;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.beetle.conference.ConferenceActivity;
import com.beetle.conference.ConferenceCommand;
import com.beetle.face.api.IMHttpFactory;
import com.beetle.face.api.body.PostDeviceToken;
import com.beetle.face.api.types.User;
import com.beetle.face.model.Contact;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.model.PhoneNumber;
import com.beetle.face.model.SyncKeyHandler;
import com.beetle.face.model.UserDB;
import com.beetle.face.tools.event.BusProvider;
import com.beetle.face.tools.event.DeviceTokenEvent;
import com.beetle.face.tools.event.LoginSuccessEvent;
import com.beetle.im.IMService;
import com.beetle.im.RTMessage;
import com.beetle.im.RTMessageObserver;
import com.beetle.im.SystemMessageObserver;
import com.beetle.voip.VOIPActivity;
import com.beetle.voip.VOIPCommand;

import com.beetle.voip.VOIPVideoActivity;
import com.beetle.voip.VOIPVoiceActivity;
import com.google.code.p.leveldb.LevelDB;
import com.huawei.android.pushagent.api.PushManager;
import com.squareup.otto.Subscribe;
import com.xiaomi.mipush.sdk.MiPushClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by houxh on 14-12-31.
 */
public class FaceApplication  extends Application implements SystemMessageObserver, RTMessageObserver {
    private final static String TAG = "face";


    private ArrayList<String> channelIDs = new ArrayList<>();

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

        im.addSystemObserver(this);
        im.addRTObserver(this);

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

    private int getNow() {
        Date now = new Date();
        return (int)(now.getTime()/1000);
    }

    @Override
    public void onSystemMessage(String sm) {
        Log.i(TAG, "system message:" + sm);
    }

    @Override
    public void onRTMessage(RTMessage rt) {
        if (Token.getInstance().uid == 0) {
            return;
        }

        if (Token.getInstance().uid != rt.receiver) {
            return;
        }

        if (VOIPActivity.activityCount > 0) {
            return;
        }

        if (ConferenceActivity.activityCount > 0) {
            return;
        }


        try {
            JSONObject json = new JSONObject(rt.content);
            if (json.has("conference")) {
                JSONObject obj = json.getJSONObject("conference");
                ConferenceCommand confCommand = new ConferenceCommand(obj);

                if (channelIDs.contains(confCommand.channelID)) {
                    return;
                }

                if (confCommand.command.equals(ConferenceCommand.COMMAND_INVITE)) {
                    channelIDs.add(confCommand.channelID);

                    String[] partipantNames = new String[confCommand.partipants.length];
                    String[] partipantAvatars = new String[confCommand.partipants.length];
                    for (int i = 0; i < confCommand.partipants.length; i++) {
                        String name = "";
                        String avatar = "";
                        User user = UserDB.getInstance().loadUser(confCommand.partipants[i]);
                        if (user != null) {
                            Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(user.zone, user.number));
                            if (c != null) {
                                name = c.displayName != null ? user.name : "";
                            }
                            avatar = user.avatar != null ? user.avatar : "";
                        }

                        partipantNames[i] = name;
                        partipantAvatars[i] = avatar;
                    }

                    Intent intent = new Intent(this, ConferenceActivity.class);
                    intent.setFlags(intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("channel_id", confCommand.channelID);
                    intent.putExtra("current_uid", Token.getInstance().uid);
                    intent.putExtra("initiator", confCommand.initiator);
                    intent.putExtra("partipants", confCommand.partipants);
                    intent.putExtra("partipant_names", partipantNames);
                    intent.putExtra("partipant_avatars", partipantAvatars);
                    startActivity(intent);
                }
            } else if (json.has("voip")) {
                JSONObject obj = json.getJSONObject("voip");
                VOIPCommand command = new VOIPCommand(obj);

                if (channelIDs.contains(command.channelID)) {
                    return;
                }
                if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL) {
                    channelIDs.add(command.channelID);

                    String name = "";
                    String avatar = "";
                    User user = UserDB.getInstance().loadUser(rt.sender);
                    if (user != null) {
                        Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(user.zone, user.number));
                        if (c != null) {
                            name = c.displayName != null ? user.name : "";
                        }
                        avatar = user.avatar != null ? user.avatar : "";
                    }

                    Intent intent = new Intent(this, VOIPVoiceActivity.class);
                    intent.setFlags(intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("peer_uid", rt.sender);
                    intent.putExtra("peer_name", name);
                    intent.putExtra("peer_avatar", avatar);
                    intent.putExtra("current_uid", Token.getInstance().uid);
                    intent.putExtra("token", Token.getInstance().accessToken);
                    intent.putExtra("is_caller", false);
                    intent.putExtra("channel_id", command.channelID);
                    startActivity(intent);
                } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
                    channelIDs.add(command.channelID);

                    String name = "";
                    String avatar = "";
                    User user = UserDB.getInstance().loadUser(rt.sender);
                    if (user != null) {
                        Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(user.zone, user.number));
                        if (c != null) {
                            name = c.displayName != null ? user.name : "";
                        }
                        avatar = user.avatar != null ? user.avatar : "";
                    }

                    Intent intent = new Intent(this, VOIPVideoActivity.class);
                    intent.setFlags(intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("peer_uid", rt.sender);
                    intent.putExtra("peer_name", name);
                    intent.putExtra("peer_avatar", avatar);
                    intent.putExtra("current_uid", Token.getInstance().uid);
                    intent.putExtra("token", Token.getInstance().accessToken);
                    intent.putExtra("is_caller", false);
                    intent.putExtra("channel_id", command.channelID);
                    startActivity(intent);
                }
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
