package com.beetle.face.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;


import com.beetle.face.BuildConfig;
import com.beetle.face.Token;
import com.beetle.face.VOIPState;
import com.beetle.face.api.types.User;
import com.beetle.face.model.Contact;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.PhoneNumber;
import com.beetle.face.model.UserDB;
import com.beetle.im.IMService;
import com.beetle.im.RTMessage;
import com.beetle.im.RTMessageObserver;
import com.beetle.im.Timer;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.react.uimanager.ViewManager;
import com.joshblour.reactnativepermissions.ReactNativePermissionsPackage;
import com.oblador.vectoricons.VectorIconsPackage;
import com.oney.WebRTCModule.WebRTCModulePackage;
import com.remobile.toast.RCTToastPackage;
import com.zmxv.RNSound.RNSoundPackage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.RunnableFuture;

import static android.os.SystemClock.uptimeMillis;

public class ConferenceActivity extends Activity implements DefaultHardwareBackBtnHandler, RTMessageObserver {
    private final String TAG = "face";

    private final int STATE_WAITING = 1;
    private final int STATE_ACCEPTED = 2;
    private final int STATE_REFUSED = 3;


    private final String COMMAND_INVITE = "invite";
    private final String COMMAND_WAIT = "waiting";
    private final String COMMAND_ACCEPT = "accept";
    private final String COMMAND_REFUSE = "refuse";

    private ReactRootView mReactRootView;
    private ReactInstanceManager mReactInstanceManager;
    private MusicIntentReceiver headsetReceiver;

    private long conferenceID;
    private long initiator;
    private long[] partipants;
    private HashMap<Long, Integer> partipantStates;

    //被呼叫
    private int state;

    private Timer inviteTimer;

    private Handler mainHandler;
    public class ConferenceModule extends ReactContextBaseJavaModule {
        public ConferenceModule(ReactApplicationContext reactContext) {
            super(reactContext);
        }

        @Override
        public String getName() {
            return "ConferenceActivity";
        }


        @ReactMethod
        public void accept() {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ConferenceActivity.this.state = STATE_ACCEPTED;
                    ConferenceActivity.this.sendAccept();
                }
            };
            mainHandler.post(r);
        }

        @ReactMethod
        public void refuse() {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ConferenceActivity.this.state = STATE_REFUSED;
                    ConferenceActivity.this.sendRefuse();
                }
            };
            mainHandler.post(r);
        }
        @ReactMethod
        public void dismiss() {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ConferenceActivity.this.finish();
                }
            };
            mainHandler.post(r);
        }

        @ReactMethod
        public void onHangUp() {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ConferenceActivity.this.finish();
                }
            };
            mainHandler.post(r);
        }

        @ReactMethod
        public void enableSpeaker() {
            AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            if (!audioManager.isWiredHeadsetOn()) {
                audioManager.setSpeakerphoneOn(true);
            } else {
                audioManager.setSpeakerphoneOn(false);
            }
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }


        @ReactMethod
        public void invalidate() {

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ConferenceActivity.this.mReactRootView.invalidate();
                }
            };
            mainHandler.post(r);
        }
    }

    class ConferencePackage implements ReactPackage {

        @Override
        public List<Class<? extends JavaScriptModule>> createJSModules() {
            return Collections.emptyList();
        }

        @Override
        public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
            return Collections.emptyList();
        }

        @Override
        public List<NativeModule> createNativeModules(
                ReactApplicationContext reactContext) {
            List<NativeModule> modules = new ArrayList<NativeModule>();

            modules.add(new ConferenceModule(reactContext));

            return modules;
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().addFlags( WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        long conferenceID = intent.getLongExtra("conference_id", 0);
        if (conferenceID == 0) {
            Log.i(TAG, "conference id is 0");
            finish();
            return;
        }
        Log.i(TAG, "conference id:" + conferenceID);

        long[] partipants = intent.getLongArrayExtra("partipants");
        if (partipants == null) {
            Log.i(TAG, "partipants is null");
            finish();
            return;
        }

        long initiator = intent.getLongExtra("initiator", 0);
        if (initiator == 0) {
            Log.i(TAG, "initiator id is 0");
            finish();
            return;
        }

        boolean isInitiator = (initiator == Token.getInstance().uid);


        mReactRootView = new ReactRootView(this);

        mReactInstanceManager = ReactInstanceManager.builder()
                .setApplication(getApplication())
                .setBundleAssetName("index.android.bundle")
                .setJSMainModuleName("index.android")
                .addPackage(new MainReactPackage())
                .addPackage(new ConferencePackage())
                .addPackage(new VectorIconsPackage())
                .addPackage(new WebRTCModulePackage())
                .addPackage(new ReactNativePermissionsPackage())
                .addPackage(new RCTToastPackage())
                .addPackage(new RNSoundPackage())
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();

        Bundle props = new Bundle();
        props.putLong("conferenceID", conferenceID);
        props.putBoolean("isInitiator", isInitiator);
        props.putLong("initiator", initiator);

        ArrayList<Bundle> users = new ArrayList<Bundle>();
        for (long p : partipants) {
            UserDB userDB = UserDB.getInstance();
            User u = userDB.loadUser(p);
            if (u != null) {
                Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(u.zone, u.number));
                if (c != null && !TextUtils.isEmpty(c.displayName)) {
                    u.name = c.displayName;
                } else {
                    u.name = u.number;
                }
                Bundle bundle = new Bundle();
                bundle.putLong("uid", u.uid);
                bundle.putString("name", u.name);
                bundle.putString("avatar", u.avatar);
                users.add(bundle);
            }
        }
        props.putParcelableArrayList("partipants", users);

        mReactRootView.startReactApplication(mReactInstanceManager, "App", props);
        setContentView(mReactRootView);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        headsetReceiver = new MusicIntentReceiver();
        mainHandler = new Handler(getMainLooper());

        this.partipants = partipants;
        this.initiator = initiator;
        this.conferenceID = conferenceID;

        if (isInitiator) {
            partipantStates = new HashMap<>();
            for (long p : partipants) {
                if (p == Token.getInstance().uid) {
                    continue;
                }

                partipantStates.put(p, STATE_WAITING);
            }

            inviteTimer = new Timer() {
                @Override
                protected void fire() {
                    ConferenceActivity.this.invite();
                }
            };

            inviteTimer.setTimer(uptimeMillis(), 1000);
            inviteTimer.resume();

        } else {
            this.state = STATE_WAITING;
        }

        IMService.getInstance().addRTObserver(this);
    }

    @Override
    public void invokeDefaultOnBackPressed() {
        super.onBackPressed();
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(headsetReceiver);
        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);

        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostResume(this, this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostDestroy();
        }

        VOIPState.getInstance().state = VOIPState.VOIP_WAITING;

        IMService.getInstance().removeRTObserver(this);

        if (this.inviteTimer != null) {
            this.inviteTimer.suspend();
            this.inviteTimer = null;
        }
    }


    @Override
    public void onBackPressed() {
        if (mReactInstanceManager != null) {
            mReactInstanceManager.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && mReactInstanceManager != null) {
            mReactInstanceManager.showDevOptionsDialog();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void sendRTMessage(String command, long to) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", this.conferenceID);
            obj.put("initiator", this.initiator);
            obj.put("command", command);
            JSONArray array = new JSONArray();
            for (long p : this.partipants) {
                array.put(p);
            }
            obj.put("partipants", array);

            JSONObject json = new JSONObject();
            json.put("conference", obj);

            RTMessage rt = new RTMessage();
            rt.sender = Token.getInstance().uid;
            rt.receiver = to;
            rt.content = json.toString();
            IMService.getInstance().sendRTMessage(rt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendRefuse() {
        sendRTMessage(COMMAND_REFUSE, this.initiator);
    }

    private void sendAccept() {
        sendRTMessage(COMMAND_ACCEPT, this.initiator);

    }

    private void sendWait() {
        sendRTMessage(COMMAND_WAIT, this.initiator);
    }

    private void sendInvite(long to) {
        sendRTMessage(COMMAND_INVITE, to);

    }

    private void invite() {
        for (long p:this.partipants) {
            if (p == Token.getInstance().uid) {
                continue;
            }

            int s = partipantStates.get(p);
            if (s == STATE_WAITING) {
                sendInvite(p);
            }
        }
    }


    private void sendEvent(ReactContext reactContext, String eventName, WritableMap params) {
        reactContext.getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);
    }

    @Override
    public void onRTMessage(RTMessage rt) {
        boolean isPartipant = false;
        for (long p : this.partipants) {
            if (p == rt.sender) {
                isPartipant = true;
                break;
            }
        }
        if (!isPartipant) {
            return;
        }
        try {
            JSONObject json = new JSONObject(rt.content);
            JSONObject obj = json.getJSONObject("conference");

            long id = obj.getLong("id");
            obj.getJSONArray("partipants");
            long initiator = obj.getLong("initiator");
            String command = obj.getString("command");

            boolean isInitiator = (this.initiator == Token.getInstance().uid);
            if (isInitiator) {
                if (command.equals(COMMAND_ACCEPT)) {
                    partipantStates.put(rt.sender, STATE_ACCEPTED);
                } else if (command.equals(COMMAND_REFUSE)) {
                    partipantStates.put(rt.sender, STATE_REFUSED);
                } else if (command.equals(COMMAND_WAIT)) {

                }

                boolean refused = true;

                for (long p : this.partipants) {
                    if (p == Token.getInstance().uid) {
                        continue;
                    }
                    int s = partipantStates.get(p);
                    if (s != STATE_REFUSED) {
                        refused = false;
                        break;
                    }
                }

                if (refused) {
                    ReactContext context = mReactInstanceManager.getCurrentReactContext();
                    if (context == null) {
                        return;
                    }
                    WritableMap p = Arguments.createMap();
                    sendEvent(context, "onRemoteRefuse", p);
                }
            } else {
                if (command.equals(COMMAND_INVITE)) {
                    if (this.state == STATE_ACCEPTED) {
                        sendAccept();
                    } else if (this.state == STATE_REFUSED) {
                        sendRefuse();
                    } else if (this.state == STATE_WAITING) {
                        sendWait();
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.d(TAG, "Headset is unplugged");
                        audioManager.setSpeakerphoneOn(true);
                        break;
                    case 1:
                        Log.d(TAG, "Headset is plugged");
                        audioManager.setSpeakerphoneOn(false);
                        break;
                    default:
                        Log.d(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }
}
