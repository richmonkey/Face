package com.beetle.face.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.beetle.VOIPEngine;
import com.beetle.face.Token;
import com.beetle.im.BytePacket;
import com.beetle.im.Timer;
import com.beetle.voip.VOIPSession;

import org.webrtc.RendererCommon;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.beetle.face.R;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 15/9/8.
 */
public class VOIPVideoActivity extends VOIPActivity {

    private VideoRenderer localRender;
    private VideoRenderer remoteRender;

    private View controlView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_voip_video);


        controlView = findViewById(R.id.control);

        GLSurfaceView renderView = (GLSurfaceView) findViewById(R.id.render);
        VideoRendererGui.setView(renderView, null);

        renderView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    VOIPVideoActivity.this.showOrHideHangUp();
                }
            }
        });

        try {
            remoteRender = VideoRendererGui.createGui(0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FIT, false);
            localRender = VideoRendererGui.createGui(70, 75, 25, 25, RendererCommon.ScalingType.SCALE_ASPECT_FIT, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onCreate(savedInstanceState);

    }

    public void switchCamera(View v) {
        if (this.voip != null) {
            this.voip.switchCamera();
        }
    }

    protected void showOrHideHangUp() {
        if (controlView.getVisibility() == View.VISIBLE) {
            hideHangUp();
        } else {
            showHangUp();
        }
    }
    private void hideHangUp() {
        controlView.setAlpha(1.0f);
        controlView.animate()
                .alpha(0.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        controlView.setVisibility(View.GONE);
                    }
                });
    }

    private void showHangUp() {
        controlView.setVisibility(View.VISIBLE);
        controlView.setAlpha(0.0f);
        controlView.animate()
                .alpha(1.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        controlView.setVisibility(View.VISIBLE);
                        controlView.setAlpha(1.0f);
                    }
                });

    }



    protected void dial() {
        this.voipSession.dialVideo();
    }

    protected void startStream() {
        super.startStream();

        if (this.voip != null) {
            Log.w(TAG, "voip is active");
            return;
        }

        try {
            if (this.voipSession.localNatMap != null && this.voipSession.localNatMap.ip != 0) {
                String ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.voipSession.localNatMap.ip)).getHostAddress();
                int port = this.voipSession.localNatMap.port;
                Log.i(TAG, "local nat map:" + ip + ":" + port);
            }
            if (this.voipSession.peerNatMap != null && this.voipSession.peerNatMap.ip != 0) {
                String ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.voipSession.peerNatMap.ip)).getHostAddress();
                int port = this.voipSession.peerNatMap.port;
                Log.i(TAG, "peer nat map:" + ip + ":" + port);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isP2P()) {
            Log.i(TAG, "start p2p stream");
        } else {
            Log.i(TAG, "start stream");
        }

        long selfUID = Token.getInstance().uid;
        String token = Token.getInstance().accessToken;

        String relayIP = this.voipSession.getRelayIP();
        Log.i(TAG, "relay ip:" + relayIP);
        String peerIP = "";
        int peerPort = 0;
        try {
            if (isP2P()) {
                peerIP = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.voipSession.peerNatMap.ip)).getHostAddress();
                peerPort = this.voipSession.peerNatMap.port;
                Log.i(TAG, "peer ip:" + peerIP + " port:" + peerPort);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        this.voip = new VOIPEngine(this.isCaller, true, token, selfUID, peer.uid, relayIP, VOIPSession.VOIP_PORT,
                peerIP, peerPort, localRender.nativeVideoRenderer, remoteRender.nativeVideoRenderer);
        this.voip.initNative();
        this.voip.start();


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        headView.setVisibility(View.GONE);
        showOrHideHangUp();
    }

    protected void stopStream() {
        super.stopStream();
        if (this.voip == null) {
            Log.w(TAG, "voip is inactive");
            return;
        }
        Log.i(TAG, "stop stream");
        this.voip.stop();
        this.voip.destroyNative();
        this.voip = null;

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy () {
        if (this.voip != null) {
            Log.e(TAG, "voip is not null");
            System.exit(1);
        }
        VideoRendererGui.dispose();
        super.onDestroy();
    }
}
