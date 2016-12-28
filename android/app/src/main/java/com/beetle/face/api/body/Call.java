package com.beetle.face.api.body;

import com.google.gson.annotations.SerializedName;

/**
 * Created by houxh on 2016/12/28.
 */

public class Call {
    @SerializedName("channel_id")
    public String channelID;
    @SerializedName("peer_uid")
    public long peerUID;
}
