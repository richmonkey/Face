package com.beetle.face.model;

/**
 * Created by houxh on 14-12-31.
 */
public class History {
    public static final int FLAG_OUT = 1;
    public static final int FLAG_CANCELED = 1<<1;
    public static final int FLAG_REFUSED = 1<<2;
    public static final int FLAG_ACCEPTED = 1<<3;
    public static final int FLAG_UNRECEIVED = 1<<4;

    public long hid;
    public long peerUID;
    public long createTimestamp;
    public long beginTimestamp;
    public long endTimestamp;
    public int flag;
}
