package com.beetle.face;

/**
 * Created by houxh on 14-12-31.
 */
public class VOIPState {
    public static final int VOIP_LISTENING = 0;
    public static final int VOIP_DIALING = 1;//呼叫对方
    public static final int VOIP_CONNECTED = 2;//通话连接成功
    public static final int VOIP_ACCEPTING = 3;//询问用户是否接听来电
    public static final int VOIP_ACCEPTED = 4;//用户接听来电
    public static final int VOIP_REFUSING = 5;//来电被拒
    public static final int VOIP_REFUSED = 6;//(来/去)电已被拒
    public static final int VOIP_HANGED_UP = 7;//通话被挂断
    public static final int VOIP_RESETED = 8;//通话连接被重置

    private static VOIPState instance = new VOIPState();
    public static VOIPState getInstance() {
        return instance;
    }


    public int state;

    public VOIPState() {
        state = VOIP_LISTENING;
    }
}
