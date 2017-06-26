package com.beetle.face.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.beetle.conference.ConferenceActivity;
import com.beetle.face.BuildConfig;
import com.beetle.face.Config;
import com.beetle.face.Token;
import com.beetle.face.api.types.User;
import com.beetle.face.model.Contact;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.PhoneNumber;
import com.beetle.face.model.UserDB;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.react.uimanager.ViewManager;
import com.joshblour.reactnativepermissions.ReactNativePermissionsPackage;
import com.oney.WebRTCModule.WebRTCModulePackage;
import com.remobile.toast.RCTToastPackage;
import com.zmxv.RNSound.RNSoundPackage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


public class ConferenceCreatorActivity extends Activity implements DefaultHardwareBackBtnHandler {
    private final String TAG = "face";

    private ReactRootView mReactRootView;
    private ReactInstanceManager mReactInstanceManager;

    private Handler mainHandler;

    private String channelID = UUID.randomUUID().toString();

    public class ConferenceCreatorModule extends ReactContextBaseJavaModule {
        public ConferenceCreatorModule(ReactApplicationContext reactContext) {
            super(reactContext);
        }

        @Override
        public String getName() {
            return "ConferenceCreatorActivity";
        }


        @ReactMethod
        public void onCreate(final ReadableArray partipants) {
            Log.i(TAG, "on create...");


            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ConferenceCreatorActivity.this.finish();
                    Intent intent = new Intent(ConferenceCreatorActivity.this, ConferenceActivity.class);
                    intent.setFlags(intent.FLAG_ACTIVITY_NEW_TASK);

                    intent.putExtra("initiator", Token.getInstance().uid);
                    intent.putExtra("channel_id", channelID);
                    intent.putExtra("current_uid", Token.getInstance().uid);

                    long[] users = new long[partipants.size()];
                    String[] partipantNames = new String[partipants.size()];
                    String[] partipantAvatars = new String[partipants.size()];
                    for (int i = 0; i < users.length; i++) {
                        users[i] = (long)partipants.getDouble(i);

                        String name = "";
                        String avatar = "";
                        User user = UserDB.getInstance().loadUser((long)partipants.getDouble(i));
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
                    intent.putExtra("partipants", users);
                    intent.putExtra("partipant_names", partipantNames);
                    intent.putExtra("partipant_avatars", partipantAvatars);


                    startActivity(intent);
                }
            };

            mainHandler.post(r);
        }

        @ReactMethod
        public void onCancel() {
            Log.i(TAG, "on cancel");
            ConferenceCreatorActivity.this.finish();
        }
    }

    class ConferenceCreatorPackage implements ReactPackage {

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

            modules.add(new ConferenceCreatorModule(reactContext));

            return modules;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mReactRootView = new ReactRootView(this);
        mReactInstanceManager = ReactInstanceManager.builder()
                .setApplication(getApplication())
                .setBundleAssetName("index.android.bundle")
                .setJSMainModuleName("index.android")
                .addPackage(new MainReactPackage())
                .addPackage(new ConferenceCreatorPackage())
                .addPackage(new WebRTCModulePackage())
                .addPackage(new ReactNativePermissionsPackage())
                .addPackage(new RCTToastPackage())
                .addPackage(new RNSoundPackage())
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();

        Bundle props = new Bundle();
        props.putLong("uid", Token.getInstance().uid);
        props.putString("token", Token.getInstance().accessToken);
        props.putString("url", Config.API_URL);

        ArrayList<Bundle> users = new ArrayList<>();
        ContactDB db = ContactDB.getInstance();
        final ArrayList<Contact> contacts = db.getContacts();
        for (int i = 0; i < contacts.size(); i++) {
            Contact c = contacts.get(i);
            for (int j = 0; j < c.phoneNumbers.size(); j++) {
                Contact.ContactData data = c.phoneNumbers.get(j);
                PhoneNumber number = new PhoneNumber();
                if (!number.parsePhoneNumber(data.value)) {
                    continue;
                }

                UserDB userDB = UserDB.getInstance();
                User u = userDB.loadUser(number);
                if (u != null) {
                    u.name = c.displayName;
                    Bundle user = new Bundle();
                    user.putLong("uid", u.uid);
                    user.putString("name", u.name);
                    users.add(user);
                }
            }
        }

        Bundle[] userBundles = new Bundle[users.size()];
        users.toArray(userBundles);
        props.putParcelableArray("users", userBundles);
        props.putString("channelID", channelID);
        mReactRootView.startReactApplication(mReactInstanceManager, "ConferenceCreator", props);
        setContentView(mReactRootView);

        mainHandler = new Handler(getMainLooper());
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
