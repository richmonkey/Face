package com.beetle.face.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
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

import com.beetle.NativeWebRtcContextRegistry;
import com.beetle.VOIP;
import com.beetle.face.Config;
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
import com.beetle.face.tools.Stun;
import com.beetle.im.BytePacket;
import com.beetle.im.IMService;
import com.beetle.im.Timer;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;
import com.squareup.picasso.Picasso;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.nio.ByteOrder;
import java.util.Date;
import java.util.Enumeration;


import static android.os.SystemClock.uptimeMillis;

public class VOIPActivity extends Activity implements VOIPObserver {

    private static final String TAG = "face";
    private User peer;
    private boolean isCaller;


    private int dialCount;
    private long dialBeginTimestamp;
    private Timer dialTimer;

    private Timer acceptTimer;
    private long acceptTimestamp;
    private Timer refuseTimer;
    private long refuseTimestamp;

    private History history = new History();

    private Button handUpButton;
    private ImageButton refuseButton;
    private ImageButton acceptButton;

    private TextView durationTextView;

    private VOIP voip;
    private int duration;
    private Timer durationTimer;

    private MediaPlayer player;

    private static Handler sHandler;

    private InetSocketAddress mappedAddr;
    private int natType = Stun.StunTypeUnknown;

    private VOIPControl.NatPortMap localNatMap;
    private VOIPControl.NatPortMap peerNatMap;


    private boolean isP2P() {
        //return false;
        if (this.localNatMap == null || this.peerNatMap == null) {
            return false;
        }
        if (this.localNatMap.ip != 0 && this.peerNatMap.ip != 0) {
            return true;
        }
        return  false;
    }

    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            int flags;
            int curApiVersion = android.os.Build.VERSION.SDK_INT;
            // This work only for android 4.4+
            if (curApiVersion >= Build.VERSION_CODES.KITKAT) {
                // This work only for android 4.4+
                // hide navigation bar permanently in android activity
                // touch the screen, the navigation bar will not show
                flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;

            } else {
                // touch the screen, the navigation bar will show
                flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }

            // must be executed in main thread :)
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_voip);

        sHandler = new Handler();
        sHandler.post(mHideRunnable);
        final View decorView = getWindow().getDecorView();
        View.OnSystemUiVisibilityChangeListener sl = new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility)
            {
                sHandler.post(mHideRunnable);
            }
        };
        decorView.setOnSystemUiVisibilityChangeListener(sl);


        handUpButton = (Button)findViewById(R.id.hang_up);
        acceptButton = (ImageButton)findViewById(R.id.accept);
        refuseButton = (ImageButton)findViewById(R.id.refuse);
        durationTextView = (TextView)findViewById(R.id.duration);

        ImageView header = (ImageView)findViewById(R.id.header);


        Intent intent = getIntent();

        isCaller = intent.getBooleanExtra("is_caller", false);
        long peerUID = intent.getLongExtra("peer_uid", 0);

        if (peerUID == 0) {
            Log.e(TAG, "peer uid is 0");
            return;
        }

        peer = loadUser(peerUID);
        if (peer == null) {
            Log.e(TAG, "load user fail");
            return;
        }

        if (!TextUtils.isEmpty(peer.avatar)) {
            Picasso.with(getBaseContext())
                    .load(peer.avatar)
                    .placeholder(R.drawable.avatar_contact)
                    .into(header);
        }

        VOIPState state = VOIPState.getInstance();
        if (isCaller) {
            this.history.flag = History.FLAG_OUT;
            handUpButton.setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.GONE);
            refuseButton.setVisibility(View.GONE);

            state.state = VOIPState.VOIP_DIALING;
            this.dialBeginTimestamp = getNow();

            sendDial();

            this.dialTimer = new Timer() {
                @Override
                protected void fire() {
                    VOIPActivity.this.sendDial();
                }
            };
            this.dialTimer.setTimer(uptimeMillis()+1000, 1000);
            this.dialTimer.resume();

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

        } else {
            handUpButton.setVisibility(View.GONE);
            acceptButton.setVisibility(View.VISIBLE);
            refuseButton.setVisibility(View.VISIBLE);

            state.state = VOIPState.VOIP_ACCEPTING;

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
        this.history.createTimestamp = getNow();
        this.history.peerUID = peerUID;

        IMService.getInstance().pushVOIPObserver(this);


        AsyncTask task = new AsyncTask<Object, Object, Boolean>() {

            private InetSocketAddress ma;
            private int natType;

            @Override
            protected Boolean doInBackground(Object[] params) {
                try {
                    Log.i(TAG, "discovery...");
                    Stun stun = new Stun();
                    int stype = stun.getNatType(Config.STUN_SERVER);

                    Log.i(TAG, "nat type:" + stype);

                    if (stype == Stun.StunTypeOpen || stype == Stun.StunTypeIndependentFilter ||
                            stype == Stun.StunTypeDependentFilter || stype == Stun.StunTypePortDependedFilter) {
                        int count = 0;
                        while (count++ < 8) {
                            InetSocketAddress ma = stun.mapPort(Config.STUN_SERVER, Config.VOIP_PORT);
                            if (ma == null) {
                                continue;
                            }
                            Log.i(TAG, "mapped address:" + ma.getAddress().getHostAddress() + ":" + ma.getPort());
                            this.ma = ma;
                            this.natType = stype;
                            return true;
                        }
                        return false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(e.getMessage());
                }
                return false;
            }
            @Override
            protected void onPostExecute(Boolean result) {
                if (!result) {
                    Log.i(TAG, "map port fail");
                    return;
                }

                VOIPActivity.this.mappedAddr = this.ma;
                VOIPActivity.this.natType = this.natType;

                if (VOIPActivity.this.localNatMap != null) {
                    return;
                }

                if (natType == Stun.StunTypeOpen || natType == Stun.StunTypeIndependentFilter ||
                        natType == Stun.StunTypeDependentFilter || natType == Stun.StunTypePortDependedFilter) {
                    try {
                        VOIPControl.NatPortMap natMap = new VOIPControl.NatPortMap();
                        natMap.ip = BytePacket.packInetAddress(ma.getAddress().getAddress());
                        natMap.port = (short)ma.getPort();
                        InetAddress localAddr = getPrimayIP();
                        if (localAddr != null) {
                            natMap.localIP = BytePacket.packInetAddress(localAddr.getAddress());
                        }
                        natMap.localPort = Config.VOIP_PORT;

                        VOIPActivity.this.localNatMap = natMap;

                        byte[] addr = BytePacket.unpackInetAddress(natMap.ip);
                        InetAddress iaddr = InetAddress.getByAddress(addr);
                        Log.i(TAG, "address:" + iaddr.getHostAddress() + "--" + ma.getAddress().getHostAddress());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            InetAddress getPrimayIP() {
                try {
                    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface current = interfaces.nextElement();
                        if (!current.isUp() || current.isLoopback() || current.isVirtual())
                            continue;
                        Enumeration<InetAddress> addresses = current.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress addr = addresses.nextElement();
                            Log.i(TAG, "host:" + addr.getHostAddress());
                            if (addr.isLoopbackAddress()) continue;
                            if (addr instanceof Inet4Address) {
                                Log.i(TAG, "primay host ip:" + addr.getHostAddress());
                                return addr;
                            }
                        }
                    }
                } catch (SocketException e) {
                    return null;
                }
                return null;
            }
        };
        task.execute();
        Log.i(TAG, "is little endian:" + ByteOrder.nativeOrder());
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
        if (this.voip != null) {
            Log.e(TAG, "voip is not null");
            System.exit(1);
        }
        VOIPState state = VOIPState.getInstance();
        state.state = VOIPState.VOIP_LISTENING;

        IMService.getInstance().popVOIPObserver(this);
        HistoryDB.getInstance().addHistory(this.history);
        Notification n = new Notification(this.history, "history");
        NotificationCenter.defaultCenter().postNotification(n);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_voip, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "keycode back");
            VOIPState state = VOIPState.getInstance();
            if (state.state == VOIPState.VOIP_DIALING) {
                this.dialTimer.suspend();
                this.dialTimer = null;
                this.player.stop();
                this.player = null;

                sendHangUp();
                state.state = VOIPState.VOIP_HANGED_UP;
                this.history.flag = this.history.flag|History.FLAG_CANCELED;

            } else if (state.state == VOIPState.VOIP_CONNECTED) {
                sendHangUp();
                state.state = VOIPState.VOIP_HANGED_UP;
                stopStream();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void hangup(View v) {
        Log.i(TAG, "hangup...");
        VOIPState state = VOIPState.getInstance();
        if (state.state == VOIPState.VOIP_DIALING) {
            this.dialTimer.suspend();
            this.dialTimer = null;
            this.player.stop();
            this.player = null;

            sendHangUp();
            state.state = VOIPState.VOIP_HANGED_UP;
            this.history.flag = this.history.flag|History.FLAG_CANCELED;

            dismiss();
        } else if (state.state == VOIPState.VOIP_CONNECTED) {
            sendHangUp();
            state.state = VOIPState.VOIP_HANGED_UP;
            stopStream();
            dismiss();
        } else {
            Log.i(TAG, "invalid voip state:" + state.state);
        }
    }

    public void accept(View v) {
        Log.i(TAG, "accepting...");
        VOIPState state = VOIPState.getInstance();
        state.state = VOIPState.VOIP_ACCEPTED;

        this.player.stop();
        this.player = null;

        this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

        if (this.localNatMap == null) {
            this.localNatMap = new VOIPControl.NatPortMap();
        }

        this.acceptTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.sendDialAccept();
            }
        };
        this.acceptTimer.setTimer(uptimeMillis()+1000, 1000);
        this.acceptTimer.resume();

        this.acceptTimestamp = getNow();
        sendDialAccept();
        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    public void refuse(View v) {
        Log.i(TAG, "refusing...");
        VOIPState state = VOIPState.getInstance();
        state.state = VOIPState.VOIP_REFUSING;
        this.player.stop();
        this.player = null;

        this.history.flag = this.history.flag|History.FLAG_REFUSED;

        this.refuseTimestamp = getNow();
        this.refuseTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.sendDialRefuse();
            }
        };
        this.refuseTimer.setTimer(uptimeMillis()+1000, 1000);
        this.refuseTimer.resume();

        sendDialRefuse();
        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    private void dismiss() {
        finish();
    }

    public void onVOIPControl(VOIPControl ctl) {
        VOIPState state = VOIPState.getInstance();

        if (ctl.sender != this.peer.uid) {
            sendTalking();
            return;
        }

        Log.i(TAG, "state:" + state.state + " command:" + ctl.cmd);
        if (state.state == VOIPState.VOIP_DIALING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

                this.peerNatMap = ctl.natMap;
                if (this.localNatMap == null) {
                    this.localNatMap = new VOIPControl.NatPortMap();
                }

                sendConnected();
                state.state = VOIPState.VOIP_CONNECTED;

                this.dialTimer.suspend();
                this.dialTimer = null;
                this.player.stop();
                this.player = null;

                Log.i(TAG, "voip connected");
                startStream();

            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_REFUSE) {
                state.state = VOIPState.VOIP_REFUSED;
                this.history.flag = this.history.flag|History.FLAG_REFUSED;
                sendRefused();


                this.dialTimer.suspend();
                this.dialTimer = null;
                this.player.stop();
                this.player = null;

                dismiss();

            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                this.dialTimer.suspend();
                this.dialTimer = null;
                this.player.stop();
                this.player = null;

                state.state = VOIPState.VOIP_ACCEPTED;
                this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

                if (this.localNatMap == null) {
                    this.localNatMap = new VOIPControl.NatPortMap();
                }
                this.acceptTimestamp = getNow();
                this.acceptTimer = new Timer() {
                    @Override
                    protected void fire() {
                        VOIPActivity.this.sendDialAccept();
                    }
                };
                this.acceptTimer.setTimer(uptimeMillis() + 1000, 1000);
                this.acceptTimer.resume();
                sendDialAccept();
            }
        } else if (state.state == VOIPState.VOIP_ACCEPTING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_HANG_UP) {
                this.player.stop();
                this.player = null;

                this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;
                state.state = VOIPState.VOIP_HANGED_UP;
                dismiss();
            }
        } else if (state.state == VOIPState.VOIP_ACCEPTED) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                this.peerNatMap = ctl.natMap;
                state.state = VOIPState.VOIP_CONNECTED;

                startStream();

                this.handUpButton.setVisibility(View.VISIBLE);
                this.acceptButton.setVisibility(View.GONE);
                this.refuseButton.setVisibility(View.GONE);
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                Log.i(TAG, "simultaneous voip connected");
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                this.peerNatMap = ctl.natMap;
                state.state = VOIPState.VOIP_CONNECTED;

                startStream();

                this.handUpButton.setVisibility(View.VISIBLE);
                this.acceptButton.setVisibility(View.GONE);
                this.refuseButton.setVisibility(View.GONE);
            }
        } else if (state.state == VOIPState.VOIP_CONNECTED) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_HANG_UP) {
                state.state = VOIPState.VOIP_HANGED_UP;

                stopStream();

                dismiss();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_RESET) {
                state.state = VOIPState.VOIP_RESETED;

                stopStream();

                dismiss();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                sendConnected();
            }
        } else if (state.state == VOIPState.VOIP_REFUSING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_REFUSED) {
                Log.i(TAG, "refuse finished");
                state.state = VOIPState.VOIP_REFUSED;

                this.refuseTimer.suspend();
                this.refuseTimer = null;

                dismiss();
            }
        }
    }

    private boolean getHeadphoneStatus() {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        boolean headphone = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        return headphone;
    }

    private void startStream() {
        if (this.voip != null) {
            Log.w(TAG, "voip is active");
            return;
        }

        try {
            if (this.localNatMap != null && this.localNatMap.ip != 0) {
                String ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.localNatMap.ip)).getHostAddress();
                int port = this.localNatMap.port;

                Log.i(TAG, "local nat map:" + ip + ":" + port);

                ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.localNatMap.localIP)).getHostAddress();
                port = this.localNatMap.localPort;

                Log.i(TAG, "local host:" + ip + ":" + port);

            }
            if (this.peerNatMap != null && this.peerNatMap.ip != 0) {
                String ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.peerNatMap.ip)).getHostAddress();
                int port = this.peerNatMap.port;
                Log.i(TAG, "peer nat map:" + ip + ":" + port);

                ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.peerNatMap.localIP)).getHostAddress();
                port = this.peerNatMap.localPort;

                Log.i(TAG, "peer local host:" + ip + ":" + port);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isP2P()) {

            Log.i(TAG, "start p2p stream");
        } else {
            Log.i(TAG, "start stream");
        }

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        this.duration = 0;
        this.durationTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.duration += 1;
                String text = String.format("%02d:%02d", VOIPActivity.this.duration/60, VOIPActivity.this.duration%60);
                Log.i(TAG, "ddd:" + text);
                durationTextView.setText(text);
            }
        };
        this.durationTimer.setTimer(uptimeMillis()+1000, 1000);
        this.durationTimer.resume();

        this.history.beginTimestamp = getNow();
        this.voip = new VOIP();
        long selfUID = Token.getInstance().uid;
        String hostIP = IMService.getInstance().getHostIP();
        boolean headphone = getHeadphoneStatus();
        String peerIP = "";
        int peerPort = 0;
        try {
            if (isP2P()) {
                if (this.peerNatMap.ip == this.localNatMap.ip && false) {
                    peerIP = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.peerNatMap.localIP)).getHostAddress();
                    peerPort = this.peerNatMap.localPort;
                } else {
                    peerIP = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.peerNatMap.ip)).getHostAddress();
                    peerPort = this.peerNatMap.port;
                }
                Log.i(TAG, "peer ip:" + peerIP + " port:" + peerPort);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        this.voip.initNative(selfUID, this.peer.uid, hostIP, peerIP, peerPort, headphone);

        this.voip.start();
    }

    private void stopStream() {
        if (this.voip == null) {
            Log.w(TAG, "voip is inactive");
            return;
        }
        Log.i(TAG, "stop stream");
        this.durationTimer.suspend();
        this.durationTimer = null;
        this.history.endTimestamp = getNow();
        this.voip.stop();
        this.voip.destroyNative();
        this.voip = null;
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    private void sendControlCommand(int cmd) {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = Token.getInstance().uid;
        ctl.receiver = this.peer.uid;
        ctl.cmd = cmd;
        IMService.getInstance().sendVOIPControl(ctl);
    }

    private void sendDial() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = Token.getInstance().uid;
        ctl.receiver = this.peer.uid;
        ctl.cmd = VOIPControl.VOIP_COMMAND_DIAL;
        ctl.dialCount = this.dialCount + 1;

        Log.i(TAG, "dial......");
        boolean r = IMService.getInstance().sendVOIPControl(ctl);
        if (r) {
            this.dialCount = this.dialCount + 1;
        } else {
            Log.i(TAG, "dial fail");
        }

        long now = getNow();
        if (now - this.dialBeginTimestamp >= 60) {
            Log.i(TAG, "dial timeout");
            this.dialTimer.suspend();
            this.dialTimer = null;
            this.player.stop();
            this.player = null;

            this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;

            dismiss();
        }
    }

    private void sendRefused() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_REFUSED);
    }

    private void sendConnected() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = Token.getInstance().uid;
        ctl.receiver = this.peer.uid;
        ctl.cmd = VOIPControl.VOIP_COMMAND_CONNECTED;
        ctl.natMap = this.localNatMap;
        IMService.getInstance().sendVOIPControl(ctl);
    }

    private void sendTalking() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_TALKING);
    }

    private void sendReset() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_RESET);
    }

    private void sendDialAccept() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = Token.getInstance().uid;
        ctl.receiver = this.peer.uid;
        ctl.cmd = VOIPControl.VOIP_COMMAND_ACCEPT;
        ctl.natMap = this.localNatMap;
        IMService.getInstance().sendVOIPControl(ctl);

        long now = getNow();
        if (now - this.acceptTimestamp >= 10) {
            Log.i(TAG, "accept timeout");
            this.acceptTimer.suspend();
            dismiss();
        }
    }

    private void sendDialRefuse() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_REFUSE);

        long now = getNow();
        if (now - this.refuseTimestamp > 10) {
            Log.i(TAG, "refuse timeout");
            this.refuseTimer.suspend();

            VOIPState state = VOIPState.getInstance();
            state.state = VOIPState.VOIP_REFUSED;
            dismiss();
        }
    }

    private void sendHangUp() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_HANG_UP);
    }
}
