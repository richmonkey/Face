package com.beetle.face.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;


import com.beetle.face.BuildConfig;
import com.beetle.face.Config;
import com.beetle.face.Token;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.react.uimanager.ViewManager;
import com.joshblour.reactnativepermissions.ReactNativePermissionsPackage;
import com.oblador.vectoricons.VectorIconsPackage;
import com.oney.WebRTCModule.WebRTCModulePackage;
import com.remobile.toast.RCTToastPackage;
import com.zmxv.RNSound.RNSoundPackage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConferenceActivity extends Activity implements DefaultHardwareBackBtnHandler {
    private final String TAG = "face";

    private ReactRootView mReactRootView;
    private ReactInstanceManager mReactInstanceManager;
    private MusicIntentReceiver headsetReceiver;

    public class ConferenceModule extends ReactContextBaseJavaModule {
        public ConferenceModule(ReactApplicationContext reactContext) {
            super(reactContext);
        }

        @Override
        public String getName() {
            return "ConferenceActivity";
        }


        @ReactMethod
        public void dismiss() {
            ConferenceActivity.this.finish();
        }

        @ReactMethod
        public void onHangUp() {
            ConferenceActivity.this.finish();
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

        boolean isInitiator = intent.getBooleanExtra("is_initiator", false);


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
        mReactRootView.startReactApplication(mReactInstanceManager, "App", props);
        setContentView(mReactRootView);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        headsetReceiver = new MusicIntentReceiver();
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
