package com.beetle.face.account;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


public class AccountService extends Service {
    private AccountAuthenticator mAccountAuthenticator = null;

    @Override
    public void onCreate() {
        mAccountAuthenticator = new AccountAuthenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mAccountAuthenticator.getIBinder();
    }
}
