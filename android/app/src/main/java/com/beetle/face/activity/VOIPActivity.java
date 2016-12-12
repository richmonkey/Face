package com.beetle.face.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.beetle.im.IMService;
import com.beetle.voip.VOIPSession;
import com.beetle.face.R;
import com.beetle.face.Token;
import com.beetle.face.VOIPState;
import com.beetle.face.api.types.User;
import com.beetle.face.model.Contact;
import com.beetle.face.model.ContactDB;
import com.beetle.face.model.History;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.model.PhoneNumber;
import com.beetle.face.model.UserDB;
import com.beetle.face.tools.Notification;
import com.beetle.face.tools.NotificationCenter;
import com.squareup.picasso.Picasso;
import java.util.Date;
import com.beetle.im.Timer;

import static android.os.SystemClock.uptimeMillis;

public class VOIPActivity extends WebRTCActivity implements VOIPSession.VOIPSessionObserver {

    protected static final String TAG = "face";


    protected String peerName;
    protected User peer;

    private History history = new History();

    protected Button handUpButton;
    protected ImageButton refuseButton;
    protected ImageButton acceptButton;
    protected ImageView  headView;
    protected TextView durationTextView;

    private int duration;
    private Timer durationTimer;

    private MediaPlayer player;



    protected VOIPSession voipSession;
    protected boolean isConnected;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        currentUID = Token.getInstance().uid;
        token = Token.getInstance().accessToken;

        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        isCaller = intent.getBooleanExtra("is_caller", false);
        peerUID = intent.getLongExtra("peer_uid", 0);
        if (peerUID == 0) {
            Log.e(TAG, "peer uid is 0");
            finish();
            return;
        }
        peer = loadUser(peerUID);
        if (peer == null) {
            Log.e(TAG, "load user fail");
            finish();
            return;
        }
        peerName = peer.name;



        voipSession = new VOIPSession(Token.getInstance().uid, peerUID);
        voipSession.setObserver(this);
        IMService.getInstance().pushVOIPObserver(this.voipSession);
        IMService.getInstance().addRTObserver(this);

        this.history.createTimestamp = getNow();
        this.history.peerUID = peerUID;
    }

    private User loadUser(long uid) {
        User u = UserDB.getInstance().loadUser(uid);
        if (u == null) {
            return null;
        }
        Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(u.zone, u.number));
        if (c == null) {
            u.name = u.number;
        } else {
            u.name = c.displayName;
        }
        return u;
    }

    @Override
    protected void onDestroy () {
        VOIPState state = VOIPState.getInstance();
        state.state = VOIPState.VOIP_WAITING;

        IMService.getInstance().popVOIPObserver(this.voipSession);
        IMService.getInstance().removeRTObserver(this);

        HistoryDB.getInstance().addHistory(this.history);
        Notification n = new Notification(this.history, "history");
        NotificationCenter.defaultCenter().postNotification(n);
        super.onDestroy();
    }



    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "keycode back");
            hangup(null);
        }
        return super.onKeyDown(keyCode, event);
    }

    public void hangup(View v) {
        Log.i(TAG, "hangup...");
        voipSession.hangup();
        if (isConnected) {
            stopStream();
            dismiss();
        } else {
            this.player.stop();
            this.player = null;
            this.history.flag = this.history.flag|History.FLAG_CANCELED;

            dismiss();
        }
    }

    public void accept(View v) {
        Log.i(TAG, "accepting...");
        voipSession.accept();
        this.player.stop();
        this.player = null;
        this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    public void refuse(View v) {
        Log.i(TAG, "refusing...");

        voipSession.refuse();

        this.player.stop();
        this.player = null;
        this.history.flag = this.history.flag|History.FLAG_REFUSED;
        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    private void dismiss() {
        finish();
    }


    private boolean getHeadphoneStatus() {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        boolean headphone = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        return headphone;
    }


    protected void dial() {
        this.history.flag = History.FLAG_OUT;
        handUpButton.setVisibility(View.VISIBLE);
        acceptButton.setVisibility(View.GONE);
        refuseButton.setVisibility(View.GONE);

        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.call);
            player = new MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setLooping(true);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);

            AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            am.setSpeakerphoneOn(false);
            am.setMode(AudioManager.STREAM_MUSIC);
            player.prepare();
            player.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void waitAccept() {
        handUpButton.setVisibility(View.GONE);
        acceptButton.setVisibility(View.VISIBLE);
        refuseButton.setVisibility(View.VISIBLE);

        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.start);
            player = new MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setLooping(true);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            am.setSpeakerphoneOn(true);
            am.setMode(AudioManager.STREAM_MUSIC);
            player.prepare();
            player.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void startStream() {
        super.startStream();
        durationTextView.setVisibility(View.VISIBLE);
        this.duration = 0;
        this.durationTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.duration += 1;
                String text = String.format("%02d:%02d", VOIPActivity.this.duration/60, VOIPActivity.this.duration%60);
                durationTextView.setText(text);
            }
        };
        this.durationTimer.setTimer(uptimeMillis()+1000, 1000);
        this.durationTimer.resume();
    }

    protected void stopStream() {
        super.stopStream();
        this.durationTimer.suspend();
        this.durationTimer = null;
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    //对方拒绝接听
    @Override
    public void onRefuse() {
        this.history.flag = this.history.flag|History.FLAG_REFUSED;
        this.player.stop();
        this.player = null;

        dismiss();
    }
    //对方挂断通话
    @Override
    public void onHangUp() {
        if (this.isConnected) {
            stopStream();
            dismiss();
        } else {
            this.player.stop();
            this.player = null;
            this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;
            dismiss();
        }
    }
    //呼叫对方时，对方正在通话
    @Override
    public void onTalking() {
        this.player.stop();
        this.player = null;
        this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;
        dismiss();
    }

    @Override
    public void onDialTimeout() {
        this.player.stop();
        this.player = null;
        this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;
        voipSession.hangup();
        dismiss();
    }

    @Override
    public void onAcceptTimeout() {
        dismiss();
    }
    @Override
    public void onConnected() {
        if (this.player != null) {
            this.player.stop();
            this.player = null;
        }

        this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

        this.handUpButton.setVisibility(View.VISIBLE);
        this.acceptButton.setVisibility(View.GONE);
        this.refuseButton.setVisibility(View.GONE);

        Log.i(TAG, "voip connected");
        startStream();

        this.isConnected = true;

    }
    @Override
    public void onRefuseFinshed() {
        dismiss();
    }
}
