package com.twizted.videoflowplayer.webrtc;

public class WebSocketConstants {


    private WebSocketConstants() {
    }

    /**
     * RED5PRO SIGNALING SERVER COMMANDS
     */

    public static final String SDP = "sdp";
    public static final String TYPE = "type";
    public static final String DATA = "data";
    public static final String STATUS = "status";
    public static final String IS_AVAILABLE = "isAvailable";
    public static final String CODE = "code";
    public static final String SOCKET_CONNECTED = "NetConnection.Connect.Success";
    public static final String SOCKET_DISCONNECTED = "NetConnection.Connect.Closed";
    public static final String STREAM_UNPUBLISHED = "NetStream.Play.UnpublishNotify";
    public static final String CODE_ICE_COMPLETED = "NetConnection.ICE.TrickleCompleted";
    public static final String MESSAGE = "message";
    public static final String REQUEST_OFFER = "requestOffer";
    public static final String REQUEST_ID = "requestId";
    public static final String TRANSPORT = "transport";
    public static final String TRANSPORT_UDP = "udp";
    public static final String HANDLE_ANSWER = "handleAnswer";
    public static final String HANDLE_CANDIDATE = "handleCandidate";
    public static final String ANSWER = "answer";
    public static final String CANDIDATE = "candidate";
    public static final String CANDIDATE_SDP_MID = "sdpMid";
    public static final String CANDIDATE_SDP_MLINE = "sdpMLineIndex";
    public static final String SUBSCRIBE = "subscribe";
    public static final String METADATA = "metadata";

}
