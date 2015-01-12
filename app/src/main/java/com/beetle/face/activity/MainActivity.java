package com.beetle.face.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
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
import com.beetle.face.tools.Notification;
import com.beetle.face.tools.NotificationCenter;
import com.beetle.im.IMService;
import com.beetle.im.Timer;

import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import butterknife.ButterKnife;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import com.beetle.face.R;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 14-8-12.
 */
public class MainActivity extends Activity implements AdapterView.OnItemClickListener,
        VOIPObserver, ContactDB.ContactObserver, NotificationCenter.NotificationCenterObserver{
    private static final String HISTORY_NAME = "history";
    ArrayList<User> users;
    ArrayList<CallHistory> callHistories;

    private Timer refreshTokenTimer;
    private int refreshErrorCount;

    private BaseAdapter contactAdapter;
    private BaseAdapter historyAdapter;

    private PagerTabStrip pagerTabStrip;


    private static class CallHistory {
        public History history;
        public User user;
    }

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
            } else {
                ImageView imageView = (ImageView) view.findViewById(R.id.header);
                imageView.setImageResource(R.drawable.avatar_contact);
            }
            return view;
        }
    }

    class HistoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return callHistories.size();
        }

        @Override
        public Object getItem(int position) {
            return callHistories.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.history, null);
            } else {
                view = convertView;
            }
            TextView tv = (TextView) view.findViewById(R.id.name);

            CallHistory h = callHistories.get(position);
            tv.setText(h.user.name);

            TextView content = ButterKnife.findById(view, R.id.content);
            String str = getCreateTimestamp(h.history.createTimestamp);
            content.setText(str);

            if (!TextUtils.isEmpty(h.user.avatar)) {
                ImageView imageView = (ImageView) view.findViewById(R.id.header);
                Picasso.with(getBaseContext())
                        .load(h.user.avatar)
                        .into(imageView);
            } else {
                ImageView imageView = (ImageView) view.findViewById(R.id.header);
                imageView.setImageResource(R.drawable.avatar_contact);
            }
            return view;
        }

        private String getCreateTimestamp(long timestamp) {
            Calendar lastDate = Calendar.getInstance();
            lastDate.setTime(new Date(timestamp*1000));
            Calendar todayDate = Calendar.getInstance();

            int year = lastDate.get(Calendar.YEAR);
            int month = lastDate.get(Calendar.MONTH);
            int day = lastDate.get(Calendar.DAY_OF_MONTH);
            int weekDay = lastDate.get(Calendar.DAY_OF_WEEK);
            int hour = lastDate.get(Calendar.HOUR_OF_DAY);
            int minute = lastDate.get(Calendar.MINUTE);
            Log.i(TAG, String.format("date:%d %d %d %d %d", year, month, day, weekDay, hour, minute));
            String str;
            if (IsSameDay(lastDate, todayDate)) {
                str = String.format("%02d:%02d", hour, minute);
            } else if (IsYestoday(lastDate, todayDate)) {
                str = String.format("昨天%02d:%02d", hour, minute);
            } else if (IsBeforeYestoday(lastDate, todayDate)) {
                str = String.format("前天%02d:%02d", hour, minute);
            } else if (IsInWeek(lastDate, todayDate)) {
                String[] t = {"", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                str = String.format("%s%02d:%02d", t[weekDay], hour, minute);
            } else if (IsInMonth(lastDate, todayDate)) {
                str = String.format("%02d-%02d-%02d %02d:%02d", year%100, month, day, hour, minute);
            } else {
                str = String.format("%04d年%02d月%02d日", year, month, day);
            }
            return str;
        }
        private boolean IsSameDay(Calendar c1, Calendar c2) {
            int year = c1.get(Calendar.YEAR);
            int month = c1.get(Calendar.MONTH);
            int day = c1.get(Calendar.DAY_OF_MONTH);

            int year2 = c2.get(Calendar.YEAR);
            int month2 = c2.get(Calendar.MONTH);
            int day2 = c2.get(Calendar.DAY_OF_MONTH);

            return (year == year2 && month == month2 && day == day2);
        }

        private boolean IsYestoday(Calendar c1, Calendar c2) {
            c2.roll(Calendar.DAY_OF_MONTH, -1);
            return IsSameDay(c1, c2);
        }

        private boolean IsBeforeYestoday(Calendar c1, Calendar c2) {
            c2.roll(Calendar.DAY_OF_MONTH, -1);
            return IsSameDay(c1, c2);
        }

        private boolean IsInWeek(Calendar c1, Calendar c2) {
            c2.roll(Calendar.DAY_OF_MONTH, -7);
            return c1.after(c2);
        }

        private boolean IsInMonth(Calendar c1, Calendar c2) {
            c2.roll(Calendar.DAY_OF_MONTH, -30);
            return c1.after(c2);
        }

    }

    public class ViewPagerAdapter extends PagerAdapter {

        public ViewPagerAdapter() {
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) 	{
            container.removeView((View)object);
        }


        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            LayoutInflater lf = getLayoutInflater().from(MainActivity.this);
            if (position == 0) {
                View historyView = lf.inflate(R.layout.call_history, null);
                container.addView(historyView, 0);
                historyAdapter = new HistoryAdapter();
                ListView lv = (ListView)historyView.findViewById(R.id.history_list);
                lv.setAdapter(historyAdapter);
                lv.setOnItemClickListener(MainActivity.this);
                return historyView;
            } else if (position == 1) {
                View contactListView = lf.inflate(R.layout.contact_list, null);
                container.addView(contactListView, 0);
                contactAdapter = new ConversationAdapter();
                ListView lv = (ListView)contactListView.findViewById(R.id.contact_list);
                lv.setAdapter(contactAdapter);
                lv.setOnItemClickListener(MainActivity.this);
                return contactListView;
            }
            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return "最近";
            } else if (position == 1) {
                return "联系人";
            } else {
                return null;
            }
        }

        @Override
        public int getCount() {
            return  2;
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0==arg1;
        }
    }

    private boolean isUserExist(User user) {
        for (User u : users) {
            if (u.uid == user.uid) {
                return true;
            }
        }
        return false;
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
                if (u != null && !isUserExist(u)) {
                    u.name = c.displayName;
                    users.add(u);
                }
            }
        }
    }

    private void loadHistory() {
        ArrayList<History> histories = HistoryDB.getInstance().loadHistoryDB();

        callHistories = new ArrayList<CallHistory>();
        for (History h : histories) {
            UserDB userDB = UserDB.getInstance();
            User u = userDB.loadUser(h.peerUID);
            Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(u.zone, u.number));
            if (c == null) {
                u.name = u.number;
            } else {
                u.name = c.displayName;
            }

            CallHistory ch = new CallHistory();
            ch.history = h;
            ch.user = u;
            Log.i(TAG, "uid:" + h.peerUID + " name:" + u.name + " number:" + u.number);
            callHistories.add(0, ch);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ContactDB.getInstance().loadContacts();
        ContactDB.getInstance().addObserver(this);

        loadUsers();
        loadHistory();

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

        NotificationCenter center = NotificationCenter.defaultCenter();
        center.addObserver(this, HISTORY_NAME);



        ViewPager vp = (ViewPager)findViewById(R.id.viewpager);
        vp.setAdapter(new ViewPagerAdapter());

        pagerTabStrip=(PagerTabStrip) findViewById(R.id.pagertab);
        pagerTabStrip.setDrawFullUnderline(false);
        pagerTabStrip.setTextSpacing(50);

    }

    @Override
    protected void onDestroy() {
        IMService.getInstance().popVOIPObserver(this);
        super.onDestroy();
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        if (parent.getId() == R.id.contact_list) {
            User u = users.get(position);

            Intent intent = new Intent(this, VOIPActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("peer_uid", u.uid);
            intent.putExtra("is_caller", true);
            startActivity(intent);
        } else if (parent.getId() == R.id.history_list) {
            CallHistory ch = callHistories.get(position);

            Intent intent = new Intent(this, VOIPActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("peer_uid", ch.history.peerUID);
            intent.putExtra("is_caller", true);
            startActivity(intent);
        }
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
    public void onNotification(Notification notification) {
        if (notification.name.equals(HISTORY_NAME)) {
            History h = (History)(notification.obj);
            CallHistory ch = new CallHistory();
            ch.history = h;
            ch.user = UserDB.getInstance().loadUser(h.peerUID);
            callHistories.add(0, ch);
            if (historyAdapter != null) {
                historyAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void OnExternalChange() {
        Log.i(TAG, "contactdb changed");
        loadUsers();
        contactAdapter.notifyDataSetChanged();
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

                        contactAdapter.notifyDataSetChanged();
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

