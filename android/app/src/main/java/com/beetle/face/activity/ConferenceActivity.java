package com.beetle.face.activity;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;


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
    }

    @Override
    public void invokeDefaultOnBackPressed() {
        super.onBackPressed();
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostResume(this, this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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
}
