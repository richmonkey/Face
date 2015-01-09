package com.beetle.face.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.beetle.face.Token;
import com.beetle.face.VOIPState;
import com.beetle.face.api.IMHttp;
import com.beetle.face.api.IMHttpFactory;
import com.beetle.face.api.body.PostAuthRefreshToken;
import com.beetle.face.api.body.PostPhone;
import com.beetle.face.api.types.User;
import com.beetle.face.model.Contact;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.History;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.model.PhoneNumber;
import com.beetle.face.model.UserDB;
import com.beetle.im.IMMessage;
import com.beetle.im.IMService;
import com.beetle.im.IMServiceObserver;
import com.beetle.im.Timer;

import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import butterknife.ButterKnife;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import com.beetle.face.R;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 14-8-12.
 */
public class MainActivity extends BaseActivity implements AdapterView.OnItemClickListener,
        VOIPObserver, ContactDB.ContactObserver{
    ArrayList<User> users;

    private Timer refreshTokenTimer;
    private int refreshErrorCount;

    private ListView lv;
    private BaseAdapter adapter;

    class ConversationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return users.size();
        }

        @Override
        public Object getItem(int position) {
            return users.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.message, null);
            } else {
                view = convertView;
            }
            TextView tv = (TextView) view.findViewById(R.id.name);
            User c = users.get(position);
            tv.setText(c.name);
            TextView content = ButterKnife.findById(view, R.id.content);
            content.setText(c.number);

            if (c.avatar != null && c.avatar.length() > 0) {
                ImageView imageView = (ImageView) view.findViewById(R.id.header);
                Picasso.with(getBaseContext())
                        .load(c.avatar)
                        .into(imageView);
            }
            return view;
        }
    }

    private void loadUsers() {
        users = new ArrayList<User>();
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
                    users.add(u);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_conversation);

        ContactDB.getInstance().loadContacts();
        ContactDB.getInstance().addObserver(this);

        loadUsers();

        adapter = new ConversationAdapter();
        lv = (ListView)findViewById(R.id.list);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(this);

        IMService.getInstance().pushVOIPObserver(this);


        Token token = Token.getInstance();
        int now = getNow();

        if (now >= token.expireTimestamp + 60) {
            refreshToken();
            //30s后刷新用户
            Handler h = new Handler();
            h.postAtTime(new Runnable() {
                @Override
                public void run() {
                    refreshUsers();
                }
            }, uptimeMillis()+30*1000);
        } else {
            int t = token.expireTimestamp - 60 - now;
            refreshTokenDelay(t);
            refreshUsers();
        }
    }

    @Override
    protected void onDestroy() {
        IMService.getInstance().popVOIPObserver(this);
        super.onDestroy();
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        User u = users.get(position);

        Intent intent = new Intent(this, VOIPActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("peer_uid", u.uid);
        intent.putExtra("is_caller", true);
        startActivity(intent);
    }

    private final static String TAG = "face";

    public void onVOIPControl(VOIPControl ctl)  {
        VOIPState state = VOIPState.getInstance();

        Log.i(TAG, "voip state:" + state.state + " command:" + ctl.cmd);
        if (state.state == VOIPState.VOIP_LISTENING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                Intent intent = new Intent(this, VOIPActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("peer_uid", ctl.sender);
                intent.putExtra("is_caller", false);
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean canBack() {
        return false;
    }

    @Override
    public void OnExternalChange() {
        Log.i(TAG, "contactdb changed");
        loadUsers();
        adapter.notifyDataSetChanged();
        refreshUsers();
    }

    void refreshUsers() {
        Log.i(TAG, "refresh user...");
        final ArrayList<Contact> contacts = ContactDB.getInstance().copyContacts();

        List<PostPhone> phoneList = new ArrayList<PostPhone>();
        HashSet<String> sets = new HashSet<String>();
        for (Contact contact : contacts) {
            if (contact.phoneNumbers != null && contact.phoneNumbers.size() > 0) {
                for (Contact.ContactData contactData : contact.phoneNumbers) {
                    PhoneNumber n = new PhoneNumber();
                    if (!n.parsePhoneNumber(contactData.value)) {
                        continue;
                    }
                    if (sets.contains(n.getZoneNumber())) {
                        continue;
                    }
                    sets.add(n.getZoneNumber());

                    PostPhone phone = new PostPhone();
                    phone.number = n.getNumber();
                    phone.zone = n.getZone();
                    phoneList.add(phone);
                }
            }
        }
        IMHttp imHttp = IMHttpFactory.Singleton();
        imHttp.postUsers(phoneList)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<ArrayList<User>>() {
                    @Override
                    public void call(ArrayList<User> users) {
                        if (users == null) return;
                        UserDB userDB = UserDB.getInstance();
                        for (int i = 0; i < users.size(); i++) {
                            User u = users.get(i);
                            if (u.uid > 0) {
                                userDB.addUser(u);
                                Log.i(TAG, "user:"+ u.uid + " number:" + u.number);
                            }
                        }
                        loadUsers();

                        adapter.notifyDataSetChanged();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.e(TAG, throwable.getMessage());
                    }
                });
    }
    private void refreshToken() {
        PostAuthRefreshToken refreshToken = new PostAuthRefreshToken();
        refreshToken.refreshToken = Token.getInstance().refreshToken;
        IMHttp imHttp = IMHttpFactory.Singleton();
        imHttp.postAuthRefreshToken(refreshToken)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Token>() {
                    @Override
                    public void call(Token token) {
                        refreshErrorCount = 0;
                        onTokenRefreshed(token);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i(TAG, "refresh token error:" + throwable);
                        refreshErrorCount++;
                        refreshTokenDelay(60*refreshErrorCount);
                    }
                });
    }

    private void refreshTokenDelay(int t) {
        if (refreshTokenTimer != null) {
            refreshTokenTimer.suspend();
        }

        refreshTokenTimer = new Timer() {
            @Override
            protected void fire() {
                refreshToken();
            }
        };
        refreshTokenTimer.setTimer(uptimeMillis() + t*1000);
        refreshTokenTimer.resume();
    }

    protected void onTokenRefreshed(Token token) {
        int now = getNow();

        Token t = Token.getInstance();
        t.accessToken = token.accessToken;
        t.refreshToken = token.refreshToken;

        t.expireTimestamp = token.expireTimestamp + now;
        t.save();
        Log.i(TAG, "token refreshed");

        int ts = token.expireTimestamp - 60 - now;
        if (ts <= 0) {
            Log.w(TAG, "expire timestamp:" + (token.expireTimestamp - now));
            return;
        }

        refreshTokenDelay(ts);
    }


    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

}

