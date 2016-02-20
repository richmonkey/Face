package com.beetle.face.tools;

/**
 * Created by houxh on 15/1/27.
 */
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class Rom {

    private static final String TAG = "face";

    private static final String KEY_MIUI_VERSION_CODE = "ro.miui.ui.version.code";
    private static final String KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name";
    private static final String KEY_MIUI_INTERNAL_STORAGE = "ro.miui.internal.storage";

    public static String getSystemProperty(String propName){
        String line = null;
        BufferedReader input = null;
        try
        {
            Process p = Runtime.getRuntime().exec("getprop " + propName);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        }
        catch (IOException ex)
        {
            Log.e(TAG, "Unable to read sysprop " + propName, ex);
            return null;
        }
        finally
        {
            if(input != null)
            {
                try
                {
                    input.close();
                }
                catch (IOException e)
                {
                    Log.e(TAG, "Exception while closing InputStream", e);
                }
            }
        }
        return line;
    }

    public static boolean isMIUI() {
        return getSystemProperty(KEY_MIUI_VERSION_CODE) != null
                || getSystemProperty(KEY_MIUI_VERSION_NAME) != null
                || getSystemProperty(KEY_MIUI_INTERNAL_STORAGE) != null;
    }

    public static boolean isH2OS() {
        String key = "ro.rom.version";
        String value = getSystemProperty(key);
        return (value != null && value.indexOf("H2OS") != -1);
    }

    public static boolean isFlyme() {
        try {
            // Invoke Build.hasSmartBar()
            final Method method = Build.class.getMethod("hasSmartBar");
            return method != null;
        } catch (final Exception e) {
            return false;
        }
    }

    public static boolean isOppo() {
        String key = "ro.build.version.opporom";
        String value = getSystemProperty(key);
        return !TextUtils.isEmpty(value);
    }

    public static void openH2OSAutoRunSetting(Context context) {
        try {
            Intent i = new Intent();
            ComponentName comp = new ComponentName("com.oneplus.security", "com.oneplus.security.autorun.AutorunMainActivity");
            i.setComponent(comp);
            context.startActivity(i);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }


    public static void openOppoAutoRunSetting(Context context) {
        try {
            Intent i = new Intent();
            ComponentName comp = new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity");
            i.setComponent(comp);
            context.startActivity(i);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void openAutoRunSetting(Context context) {
        if (isOppo()) {
            openOppoAutoRunSetting(context);
        } else if (isH2OS()) {
            openH2OSAutoRunSetting(context);
        }
    }
}
