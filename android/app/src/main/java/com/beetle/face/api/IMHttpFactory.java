package com.beetle.face.api;

/**
 * Created by tsung on 10/10/14.
 */
public class IMHttpFactory {
    static final Object monitor = new Object();
    static IMHttp singleton;
    static IMHttp sdkSingleton;

    public static IMHttp Singleton() {
        if (singleton == null) {
            synchronized (monitor) {
                if (singleton == null) {
                    singleton = new IMHttpRetrofit().getService();
                }
            }
        }

        return singleton;
    }

    public static IMHttp SDKSingleton() {
        if (sdkSingleton == null) {
            synchronized (monitor) {
                if (sdkSingleton == null) {
                    sdkSingleton = new IMHttpRetrofit().getSdkService();
                }
            }
        }

        return sdkSingleton;
    }
}
