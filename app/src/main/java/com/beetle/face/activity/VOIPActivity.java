package com.beetle.face.activity;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

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
import com.beetle.im.IMService;
import com.beetle.im.Timer;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;

import java.util.Date;

import static android.os.SystemClock.uptimeMillis;

public class VOIPActivity extends ActionBarActivity implements VOIPObserver {

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
    private Button refuseButton;
    private Button acceptButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voip);

        handUpButton = (Button)findViewById(R.id.hang_up);
        acceptButton = (Button)findViewById(R.id.accept);
        refuseButton = (Button)findViewById(R.id.refuse);

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

        VOIPState state = VOIPState.getInstance();
        if (isCaller) {
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

        } else {
            handUpButton.setVisibility(View.GONE);
            acceptButton.setVisibility(View.VISIBLE);
            refuseButton.setVisibility(View.VISIBLE);

            state.state = VOIPState.VOIP_ACCEPTING;
        }
        this.history.createTimestamp = getNow();

        IMService.getInstance().pushVOIPObserver(this);
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
        state.state = VOIPState.VOIP_LISTENING;

        IMService.getInstance().popVOIPObserver(this);
        HistoryDB.getInstance().addHistory(this.history);

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

    public void hangup(View v) {
        Log.i(TAG, "hangup...");
        VOIPState state = VOIPState.getInstance();
        if (state.state == VOIPState.VOIP_DIALING) {
            this.dialTimer.suspend();
            this.dialTimer = null;
            sendHangUp();
            state.state = VOIPState.VOIP_HANGED_UP;
            this.history.flag = this.history.flag|History.FLAG_CANCELED;

            dismiss();
        } else if (state.state == VOIPState.VOIP_CONNECTED) {
            sendHangUp();
            state.state = VOIPState.VOIP_HANGED_UP;
            //todo stop stream

            dismiss();
        } else {
            Log.i(TAG, "invalid voip state:" + state.state);
        }
    }

    public void accept(View v) {
        Log.i(TAG, "accepting...");
        VOIPState state = VOIPState.getInstance();
        state.state = VOIPState.VOIP_ACCEPTED;

        this.history.flag = this.history.flag|History.FLAG_ACCEPTED;
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

                sendConnected();
                state.state = VOIPState.VOIP_CONNECTED;

                this.dialTimer.suspend();
                this.dialTimer = null;


                Log.i(TAG, "voip connected");
                //todo start stream

            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_REFUSE) {
                state.state = VOIPState.VOIP_REFUSED;
                this.history.flag = this.history.flag|History.FLAG_REFUSED;
                sendRefused();


                this.dialTimer.suspend();
                this.dialTimer = null;

                dismiss();

            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                this.dialTimer.suspend();
                this.dialTimer = null;

                state.state = VOIPState.VOIP_ACCEPTED;
                this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

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
                this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;
                state.state = VOIPState.VOIP_HANGED_UP;
                dismiss();
            }
        } else if (state.state == VOIPState.VOIP_ACCEPTED) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                state.state = VOIPState.VOIP_CONNECTED;

                //todo start stream

                this.handUpButton.setVisibility(View.VISIBLE);
                this.acceptButton.setVisibility(View.GONE);
                this.refuseButton.setVisibility(View.GONE);
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                Log.i(TAG, "simultaneous voip connected");
                this.acceptTimer.suspend();
                this.acceptTimer = null;
                state.state = VOIPState.VOIP_CONNECTED;

                //todo start stream

                this.handUpButton.setVisibility(View.VISIBLE);
                this.acceptButton.setVisibility(View.GONE);
                this.refuseButton.setVisibility(View.GONE);
            }
        } else if (state.state == VOIPState.VOIP_CONNECTED) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_HANG_UP) {
                state.state = VOIPState.VOIP_HANGED_UP;

                //todo stop stream
                dismiss();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_RESET) {
                state.state = VOIPState.VOIP_RESETED;

                //todo stop stream
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

            this.history.flag = this.history.flag|History.FLAG_UNRECEIVED;

            dismiss();
        }
    }

    private void sendRefused() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_REFUSED);
    }

    private void sendConnected() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_CONNECTED);
    }

    private void sendTalking() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_TALKING);
    }

    private void sendReset() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_RESET);
    }

    private void sendDialAccept() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_ACCEPT);

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
