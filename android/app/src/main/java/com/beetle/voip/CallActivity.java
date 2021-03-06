package com.beetle.voip;

import android.os.Bundle;
import android.util.Log;


import com.beetle.face.api.IMHttp;
import com.beetle.face.api.IMHttpFactory;
import com.beetle.face.api.body.Call;
import com.beetle.face.model.History;
import com.beetle.face.model.HistoryDB;
import com.beetle.face.tools.Notification;
import com.beetle.face.tools.NotificationCenter;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by houxh on 2016/12/27.
 */

public class CallActivity extends VOIPActivity {

    private History history = new History();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.history.createTimestamp = getNow();
    }

    @Override
    protected void onDestroy () {
        super.onDestroy();

        if (isCaller) {
            this.history.flag = this.history.flag | History.FLAG_OUT;
        }
        this.history.endTimestamp = getNow();
        this.history.peerUID = peerUID;
        HistoryDB.getInstance().addHistory(this.history);
        Notification n = new Notification(this.history, "history");
        NotificationCenter.defaultCenter().postNotification(n);
    }

    @Override
    public void dialVideo() {
        super.dialVideo();
        IMHttp imHttp = IMHttpFactory.Singleton();
        Call call = new Call();

        call.channelID = this.channelID;
        call.peerUID = this.peerUID;

        imHttp.postCall(call)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        Log.i(TAG, "post call success");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i(TAG, "post call fail");
                    }
                });
    }

    @Override
    public void dialVoice() {
        super.dialVoice();
        IMHttp imHttp = IMHttpFactory.Singleton();
        Call call = new Call();

        call.channelID = this.channelID;
        call.peerUID = this.peerUID;

        imHttp.postCall(call)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        Log.i(TAG, "post call success");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i(TAG, "post call fail");
                    }
                });
    }


    @Override
    public void hangup() {
        super.hangup();
        if (!isConnected) {
            this.history.flag = this.history.flag|History.FLAG_CANCELED;
        }
    }

    @Override
    public void accept() {
        super.accept();
        this.history.flag = this.history.flag|History.FLAG_ACCEPTED;
    }

    @Override
    public void refuse() {
        super.refuse();
        this.history.flag = this.history.flag|History.FLAG_REFUSED;
    }

    @Override
    public void onRefuse() {
        super.onRefuse();
        this.history.flag = this.history.flag|History.FLAG_REFUSED;
    }

    @Override
    public void onConnected() {
        super.onConnected();
        this.history.flag = this.history.flag|History.FLAG_ACCEPTED;

    }

    @Override
    protected void startStream() {
        super.startStream();
        this.history.beginTimestamp = getNow();
    }

    @Override
    protected void stopStream() {
        super.stopStream();
        this.history.endTimestamp = getNow();
    }

}
