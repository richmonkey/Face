package com.beetle.face.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.beetle.face.Config;
import com.beetle.face.Token;
import com.beetle.face.VOIPState;
import com.beetle.face.api.IMHttp;
import com.beetle.face.api.IMHttpFactory;
import com.beetle.face.api.body.PostAuthRefreshToken;
import com.beetle.face.api.body.PostPhone;
import com.beetle.face.api.types.User;
import com.beetle.face.api.types.Version;
import com.beetle.face.model.ApplicationDB;
import com.beetle.face.model.Contact;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.History;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.model.PhoneNumber;
import com.beetle.face.model.UserDB;
import com.beetle.face.tools.Notification;
import com.beetle.face.tools.NotificationCenter;
import com.beetle.face.tools.Rom;
import com.beetle.voip.VOIPService;
import com.beetle.im.Timer;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
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
public class MainActivity extends Activity implements AdapterView.OnItemClickListener,
        ContactDB.ContactObserver, NotificationCenter.NotificationCenterObserver{
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

    class  ContactAdapter extends BaseAdapter {
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
                        .placeholder(R.drawable.avatar_contact)
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

            ImageView flagView = (ImageView)view.findViewById(R.id.flag);

            CallHistory h = callHistories.get(position);
            tv.setText(h.user.name);

            if ((h.history.flag&History.FLAG_OUT) != 0) {
                flagView.setImageResource(R.drawable.call_out);
            } else if ((h.history.flag&History.FLAG_UNRECEIVED) != 0){
                flagView.setImageResource(R.drawable.callin_not_answer);
            } else {
                flagView.setImageResource(R.drawable.call_in);
            }

            TextView content = ButterKnife.findById(view, R.id.content);
            String str = getCreateTimestamp(h.history.createTimestamp);
            content.setText(str);

            if (!TextUtils.isEmpty(h.user.avatar)) {
                ImageView imageView = (ImageView) view.findViewById(R.id.header);
                Picasso.with(getBaseContext())
                        .load(h.user.avatar)
                        .placeholder(R.drawable.avatar_contact)
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
                View tv = historyView.findViewById(R.id.tip);
                historyAdapter = new HistoryAdapter();
                ListView lv = (ListView)historyView.findViewById(R.id.history_list);
                lv.setAdapter(historyAdapter);
                lv.setOnItemClickListener(MainActivity.this);

                if (callHistories.size() > 0) {
                    lv.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.GONE);
                } else {
                    lv.setVisibility(View.GONE);
                    tv.setVisibility(View.VISIBLE);
                }

                return historyView;
            } else if (position == 1) {
                View contactListView = lf.inflate(R.layout.contact_list, null);
                container.addView(contactListView, 0);
                contactAdapter = new ContactAdapter();

                ListView lv = (ListView)contactListView.findViewById(R.id.contact_list);
                lv.setAdapter(contactAdapter);
                lv.setOnItemClickListener(MainActivity.this);

                View headView  = getLayoutInflater().inflate(R.layout.my_number, null);

                TextView textView = (TextView)headView.findViewById(R.id.number);
                User u = UserDB.getInstance().loadUser(Token.getInstance().uid);
                String number = String.format("+%s %s", u.zone, u.number);
                textView.setText(number);

                lv.addHeaderView(headView);

                View footView  = getLayoutInflater().inflate(R.layout.share_item, null);
                lv.addFooterView(footView);

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

        IMHttp imHttp = IMHttpFactory.Singleton();
        imHttp.getLatestVersion()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Version>() {
                    @Override
                    public void call(Version obj) {
                        MainActivity.this.checkVersion(obj);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i(TAG, "get latest version fail:" + throwable.getMessage());
                    }
                });

        createSyncAccount();
        openAutoRunSetting();
        Log.i(TAG, "main activity oncreate");
    }

    private void openAutoRunSetting() {
        if (!ApplicationDB.getInstance().firstRun) {
            return;
        }

        ApplicationDB.getInstance().firstRun = false;
        ApplicationDB.getInstance().save();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("为了保证正常拨打与接听免费电话，请开启蜗牛电话“自启动权限”。");
        builder.setTitle("提示");
        builder.setPositiveButton("确认", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Rom.openAutoRunSetting(MainActivity.this);
            }
        });
        builder.setNegativeButton("取消", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void checkVersion(final Version version) {
        Log.i(TAG, "latest version:" + version.major + ":" + version.minor + " url:" + version.url);
        int versionCode = version.major*10+version.minor;
        PackageManager pm = this.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(getPackageName(), 0);
            if (versionCode > info.versionCode) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("是否更新电话虫?");
                builder.setTitle("提示");
                builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(version.url));
                        startActivity(browserIntent);
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.create().show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "main activity ondestroy");
        super.onDestroy();
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {

        if (parent.getId() == R.id.contact_list) {
            if (id == -1 && position == users.size() + 1) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:"));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                String body = String.format("我正在使用“电话虫”。 %s 可以给您的联系人拨打语音电话。", Config.DOWNLOAD_URL);
                intent.putExtra("sms_body", body);
                startActivity(intent);
                return;
            }
            if (id == -1) {
                return;
            }
        }

        VOIPState state = VOIPState.getInstance();
        if (VOIPService.getInstance().getConnectState() != VOIPService.ConnectState.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), "网络未链接",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        long peerUID = 0;
        if (parent.getId() == R.id.contact_list) {
            User u = users.get((int)id);
            peerUID = u.uid;
        } else if (parent.getId() == R.id.history_list) {
            CallHistory ch = callHistories.get(position);
            peerUID = ch.history.peerUID;
        } else {
            Log.i(TAG, "invalid click");
            return;
        }

        final CharSequence[] items = {
                "语音呼叫", "视频呼叫"
        };

        final long calleeUID = peerUID;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                VOIPState state = VOIPState.getInstance();
                if (item == 0) {
                    Intent intent = new Intent(MainActivity.this, VOIPVoiceActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("peer_uid", calleeUID);
                    intent.putExtra("is_caller", true);
                    state.state = VOIPState.VOIP_TALKING;
                    startActivity(intent);
                } else if (item == 1){
                    Intent intent = new Intent(MainActivity.this, VOIPVideoActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("peer_uid", calleeUID);
                    intent.putExtra("is_caller", true);
                    state.state = VOIPState.VOIP_TALKING;
                    startActivity(intent);
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private final static String TAG = "face";



    @Override
    public void onNotification(Notification notification) {
        if (notification.name.equals(HISTORY_NAME)) {
            History h = (History)(notification.obj);
            CallHistory ch = new CallHistory();
            ch.history = h;
            ch.user = UserDB.getInstance().loadUser(h.peerUID);
            Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(ch.user.zone, ch.user.number));
            if (c == null) {
                ch.user.name = ch.user.number;
            } else {
                ch.user.name = c.displayName;
            }
            callHistories.add(0, ch);
            if (callHistories.size() == 1) {
                View t = this.findViewById(R.id.tip);
                View hl = this.findViewById(R.id.history_list);
                if (t != null) {
                    t.setVisibility(View.GONE);
                }
                if (hl != null) {
                    hl.setVisibility(View.VISIBLE);
                }
            }
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

        VOIPService.getInstance().setToken(token.accessToken);

        Token t = Token.getInstance();
        t.accessToken = token.accessToken;
        t.refreshToken = token.refreshToken;

        t.expireTimestamp = token.expireTimestamp + now;
        t.save();
        Log.i(TAG, "token refreshed");

        int ts = token.expireTimestamp - 60;
        if (ts <= 0) {
            Log.w(TAG, "expire timestamp:" + token.expireTimestamp);
            return;
        }

        refreshTokenDelay(ts);
    }


    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    private static final String ACCOUNT_TYPE = "com.beetle.face";
    public static final String CONTENT_AUTHORITY = "com.android.contacts";
    private static final long SYNC_FREQUENCY = 60;  //(in seconds)

    public void createSyncAccount() {
        User u = UserDB.getInstance().loadUser(Token.getInstance().uid);
        if (u == null) {
            return;
        }
        Account account = new Account(u.number, ACCOUNT_TYPE);
        AccountManager accountManager = (AccountManager) getSystemService(Context.ACCOUNT_SERVICE);
        if (accountManager.addAccountExplicitly(account, null, null)) {
            // Inform the system that this account supports sync
            ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true);
            // Recommend a schedule for automatic synchronization. The system may modify this based
            // on other scheduled syncs and network utilization.
            ContentResolver.addPeriodicSync(
                    account, CONTENT_AUTHORITY, new Bundle(),SYNC_FREQUENCY);
            Log.i(TAG, "add account periodic sync");
        }
    }


}

