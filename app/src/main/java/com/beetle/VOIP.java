package com.beetle;

public class VOIP {
	private long nativeVOIP;

	public native void initNative(long selfUID, long peerUID, String hostIP, String peerIP, int peerPort, boolean isHeadphone);
	public native void destroyNative();
	public native void start();	
	public native void stop();
    public native void listenVOIP();
    public native void closeUDP();

    static {
        System.loadLibrary("voip");
    }
}