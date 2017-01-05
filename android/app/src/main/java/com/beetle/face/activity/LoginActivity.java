package com.beetle.face.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.beetle.face.R;
import com.beetle.face.Token;
import com.beetle.face.api.IMHttp;
import com.beetle.face.api.IMHttpFactory;
import com.beetle.face.api.body.PostAuthToken;
import com.beetle.face.api.types.Code;
import com.beetle.face.api.types.User;
import com.beetle.face.model.UserDB;
import com.beetle.face.tools.event.BusProvider;
import com.beetle.face.tools.event.LoginSuccessEvent;
import com.beetle.im.IMService;
import com.beetle.im.Timer;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 14-8-11.
 */
public class LoginActivity extends AccountActivity implements TextView.OnEditorActionListener {
    private final String TAG = "beetle";

    private long timestamp;
    private int beginQuery;
    private Timer queryTimer;


    @InjectView(R.id.text_phone)
    EditText phoneText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        ButterKnife.inject(this);

        Token t = Token.getInstance();
        Log.i(TAG, "access token:" + t.accessToken + " uid:" + t.uid);
        if (t.accessToken != null) {
            Log.i(TAG, "current uid:" + t.uid);
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }

        phoneText.setOnEditorActionListener(this);
    }

    @OnClick(R.id.btn_verify_code)
    void getVerifyCode() {
        Log.i(TAG, "get verify code");
        final String phone = phoneText.getText().toString();
        if (phone.length() != 11) {
            Toast.makeText(getApplicationContext(), "非法的手机号码", Toast.LENGTH_SHORT).show();
            return;
        }

        timestamp = getNow();
        final ProgressDialog dialog = ProgressDialog.show(this, null, "Request...");
        IMHttp imHttp = IMHttpFactory.Singleton();
        imHttp.getVerifyCode("86", phone)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Code>() {
                    @Override
                    public void call(Code code) {
                        dialog.dismiss();
                        searchSMS(phone);
                        Log.i(TAG, "code:" + code.code);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i(TAG, "request code fail");
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "获取验证码失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private String matchSMS(String body) {
        //sms template "尊敬的用户,您的注册验证码是773322,感谢您使用momo！";
        if (body.indexOf("电话虫") != -1 && body.indexOf("您的注册验证码是") != -1) {
            Pattern p = Pattern.compile("您的注册验证码是([0-9]{6})", Pattern.CASE_INSENSITIVE);
            Matcher match = p.matcher(body);
            if (!match.find()) {
                return "";
            } else {
                String code = match.group(1);
                Log.d(TAG, "code:" + code);
                return code;
            }
        }
        return "";
    }

    private String getVerifyCodeFromSMS() {
        long timestamp = this.timestamp*1000;
        Uri inboxURI = Uri.parse("content://sms/inbox");
        Log.d(TAG, "search timestamp:" + timestamp);
        Cursor c = getContentResolver().query(inboxURI,
                null,
                "date>?",
                new String[]{
                        String.valueOf(timestamp)
                },
                null);

        if (c == null || c.getCount() < 1) {
            return "";
        }

        if (c.moveToFirst()) {
            do {
                String strBody = c.getString(c.getColumnIndex("body"));
                String code = matchSMS(strBody);
                if (!TextUtils.isEmpty(code)) {
                    return code;
                }
            } while (c.moveToNext());
        }
        return "";
    }

    private void searchSMS(final String phone) {
        final ProgressDialog dialog = ProgressDialog.show(this, null, "收取短信...");
        queryTimer = new Timer() {
            @Override
            protected void fire() {
                long now = getNow();
                if (now - beginQuery >= 10) {
                    Log.i(TAG, "read verify code from sms timeout");
                    dialog.dismiss();
                    queryTimer.suspend();
                    startActivity(VerifyActivity.newIntent(LoginActivity.this, phone));
                    return;
                }

                String verifyCode = getVerifyCodeFromSMS();
                if (!TextUtils.isEmpty(verifyCode)) {
                    dialog.dismiss();
                    queryTimer.suspend();
                    verifySMSCode(phone, verifyCode);
                }
            }
        };
        beginQuery = getNow();
        queryTimer.setTimer(uptimeMillis(), 1000);
        queryTimer.resume();
    }

    void verifySMSCode(final String phone, final String code) {
        final ProgressDialog dialog = ProgressDialog.show(this, null, "校验验证码...");

        PostAuthToken postAuthToken = new PostAuthToken();
        postAuthToken.code = code;
        postAuthToken.zone = "86";
        postAuthToken.number = phone;
        IMHttp imHttp = IMHttpFactory.Singleton();
        imHttp.postAuthToken(postAuthToken)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Token>() {
                    @Override
                    public void call(Token token) {
                        dialog.dismiss();

                        Token t = Token.getInstance();
                        t.accessToken = token.accessToken;
                        t.refreshToken = token.refreshToken;
                        t.expireTimestamp = token.expireTimestamp + getNow();
                        t.uid = token.uid;

                        t.save();

                        User u = new User();
                        u.uid = token.uid;
                        u.number = phone;
                        u.zone = "86";
                        UserDB.getInstance().addUser(u);

                        IMService im = IMService.getInstance();
                        im.setToken(token.accessToken);
                        im.start();

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        BusProvider.getInstance().post(new LoginSuccessEvent());
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i(TAG, "auth token fail");
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "登录失败", Toast.LENGTH_SHORT).show();
                    }
                });

        Log.i(TAG, "code:" + code);
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    @Override
    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        if (i == EditorInfo.IME_ACTION_NEXT) {
            getVerifyCode();
        }
        return false;
    }
}
