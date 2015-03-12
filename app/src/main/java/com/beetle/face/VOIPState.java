package com.beetle.face;

/**
 * Created by houxh on 14-12-31.
 */
public class VOIPState {
    public static final int VOIP_WAITING = 0;//等待呼叫
    public static final int VOIP_TALKING = 1;//通话中


    private static VOIPState instance = new VOIPState();
    public static VOIPState getInstance() {
        return instance;
    }


    public int state;

    public VOIPState() {
        state = VOIP_WAITING;
    }
}
