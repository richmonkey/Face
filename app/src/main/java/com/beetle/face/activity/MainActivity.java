package com.beetle.face.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.beetle.im.IMMessage;
import com.beetle.im.IMService;
import com.beetle.im.IMServiceObserver;
import com.beetle.im.Timer;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by houxh on 14-8-8.
 */


public class MainActivity extends BaseActivity {

    private static final String SEND_MESSAGE_NAME = "send_message";



    ListView lv;

    private static final String TAG = "beetle";

    private Timer refreshTimer;

    private android.support.v7.app.ActionBar actionBar;
    private BaseAdapter adapter;
    class ConversationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return 0;
        }
        @Override
        public Object getItem(int position) {
            return null;
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            return null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "main activity create...");

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    public boolean canBack() {
        return false;
    }
}
