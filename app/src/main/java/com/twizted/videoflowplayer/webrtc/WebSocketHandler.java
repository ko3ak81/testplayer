package com.twizted.videoflowplayer.webrtc;

import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;


public class WebSocketHandler implements WebSocket.WebSocketConnectionObserver {
    private static final String TAG = "WSChannelNativeClient";
    private static final int CLOSE_TIMEOUT = 1000;
    private WebSocketConnection ws;
    private final Handler handler;
    private String wsServerUrl;
    private final Object closeEventLock = new Object();
    private boolean closeEvent;
    private Red5MediaSignallingEvents signallingListener;

    private String streamName;
    private String subscribeId;
    private int candidateNumber;

    private static final String alphabetNum = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static SecureRandom rnd = new SecureRandom();


    public WebSocketHandler(Red5MediaSignallingEvents signallingListener, Handler handler, String streamName) {
        this.handler = handler;
        this.signallingListener = signallingListener;
        this.streamName = streamName;
        this.candidateNumber = 0;
    }

    public void connect(final String wsUrl) {
        checkIfCalledOnValidThread();
        wsServerUrl = wsUrl;
        Log.d(TAG, "Connecting WebSocket to: " + wsUrl);
        ws = new WebSocketConnection();
        try {
            ws.connect(new URI(wsServerUrl), this);
        } catch (WebSocketException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void sendTextMessage(String message) {
        if (ws.isConnected()) {
            ws.sendTextMessage(message);
            Log.d(TAG, "sent websocket message:" + message);
        } else {
            Log.d(TAG, "Web Socket is not connected");
        }
    }

    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();
        Log.d(TAG, "Disconnect WebSocket.");
        ws.disconnect();
        // Wait for websocket close event to prevent websocket library from
        // sending any pending messages to deleted looper thread.
        if (waitForComplete) {
            synchronized (closeEventLock) {
                while (!closeEvent) {
                    try {
                        closeEventLock.wait(CLOSE_TIMEOUT);
                        break;
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Wait error: " + e.toString());
                    }
                }
            }
        }
        Log.d(TAG, "Disconnecting WebSocket done.");
    }

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    @Override
    public void onOpen() {

    }

    @Override
    public void onClose(WebSocketCloseNotification webSocketCloseNotification, String s) {
        Log.d(TAG, "WebSocket connection closed.");
        synchronized (closeEventLock) {
            closeEvent = true;
            closeEventLock.notify();
        }
    }

    @Override
    public void onTextMessage(String msg) {
        Log.e(TAG, "onTextMessage: "+msg);
        if (!ws.isConnected()) {
            Log.e(TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(msg);
            if (json.has(WebSocketConstants.IS_AVAILABLE)) {
                if (json.getBoolean(WebSocketConstants.IS_AVAILABLE)) {
                    // Request stream
                    requestStream();
                } else {
                    // Stream not available. Disconnect and try again with scheduler
                    signallingListener.noStreamExistsToPlay(streamName);
                    disconnect(true);
                }
            } else if (json.has(WebSocketConstants.TYPE) && json.getString(WebSocketConstants.TYPE).equals(WebSocketConstants.METADATA)) {
                signallingListener.onPlayStarted(streamName);
            } else if (json.has(WebSocketConstants.DATA)) {
                JSONObject data = json.getJSONObject(WebSocketConstants.DATA);
                if (data.has(WebSocketConstants.TYPE)) {
                    String dataType = data.getString(WebSocketConstants.TYPE);
                    if (dataType.equals(WebSocketConstants.STATUS)) {
                        if (data.has(WebSocketConstants.CODE)) {
                            String code = data.getString(WebSocketConstants.CODE);
                            if (code.equals(WebSocketConstants.SOCKET_CONNECTED)) {
                                // check if stream is available
//                                checkIfStreamIsValid();
                            } else if (code.equals(WebSocketConstants.CODE_ICE_COMPLETED)) {
                                // subscribe to stream
                                subscribeToStream();
                            }
                        } else {
                            // We got message with information
                            String message = data.getString(WebSocketConstants.MESSAGE);

                        }
                    } else if (dataType.equals(WebSocketConstants.CANDIDATE)) {
                        JSONObject candidateObj = data.getJSONObject(WebSocketConstants.CANDIDATE);

                        if (candidateObj.length() == 0) {
                            return;
                        }
                        String sdpMid = candidateObj.getString(WebSocketConstants.CANDIDATE_SDP_MID);
                        int sdpMLineIndex = candidateObj.getInt(WebSocketConstants.CANDIDATE_SDP_MLINE);
                        String sdp = candidateObj.getString(WebSocketConstants.CANDIDATE);

                        IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);

                        signallingListener.onRemoteIceCandidate(streamName, candidate);
                    }
                } else if (data.has(WebSocketConstants.SDP)) {
                    // We got SDP configuration
                    JSONObject sdpJson = data.getJSONObject(WebSocketConstants.SDP);
                    String type = sdpJson.getString(WebSocketConstants.TYPE);
                    String description = sdpJson.getString(WebSocketConstants.SDP);
                    SessionDescription.Type sdpType = SessionDescription.Type.fromCanonicalForm(type);
                    SessionDescription sdp = new SessionDescription(sdpType, description);

//                    signallingListener.onTakeConfiguration(streamId, sdp);
                    signallingListener.onTakeConfiguration(streamName, sdp);
                } else if (data.has(WebSocketConstants.STATUS) &&
                        data.getString(WebSocketConstants.STATUS).equals(WebSocketConstants.STREAM_UNPUBLISHED)) {

                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "WebSocket message JSON parsing error: " + e.toString());
        }

    }

    public void checkIfStreamIsValid() {
        checkIfCalledOnValidThread();
        JSONObject json = new JSONObject();
        try {
            json.put(WebSocketConstants.IS_AVAILABLE, streamName);
            sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void requestStream() {
        checkIfCalledOnValidThread();
        subscribeId = "subscriber-"+randomString(4);
        JSONObject json = new JSONObject();
        try {
            json.put(WebSocketConstants.REQUEST_OFFER, streamName);
            json.put(WebSocketConstants.REQUEST_ID, subscribeId);
            json.put(WebSocketConstants.TRANSPORT, WebSocketConstants.TRANSPORT_UDP);
            sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void subscribeToStream() {
        checkIfCalledOnValidThread();
        JSONObject json = new JSONObject();
        try {
            json.put(WebSocketConstants.SUBSCRIBE, streamName);
            json.put(WebSocketConstants.REQUEST_ID, subscribeId);
            sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRawTextMessage(byte[] bytes) {

    }

    @Override
    public void onBinaryMessage(byte[] bytes) {

    }

    public void startPlay(){
        checkIfStreamIsValid();
    }

    public void sendConfiguration(final SessionDescription sdp, String type) {
        checkIfCalledOnValidThread();
        JSONObject json = new JSONObject();
        try {
            json.put(WebSocketConstants.HANDLE_ANSWER, streamName);
            json.put(WebSocketConstants.REQUEST_ID, subscribeId);

            JSONObject sdpInfo = new JSONObject();
            sdpInfo.put(WebSocketConstants.TYPE, type);
            sdpInfo.put(WebSocketConstants.SDP, sdp.description);

            JSONObject sdpObj = new JSONObject();
            sdpObj.put(WebSocketConstants.SDP, sdpInfo);

            json.put(WebSocketConstants.DATA, sdpObj);
            Log.e(TAG, "sendConfiguration: "+json.toString());
            sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendLocalIceCandidate(final IceCandidate candidate) {
        checkIfCalledOnValidThread();
        this.candidateNumber++;
        if (candidateNumber == 1) {
            this.handler.postDelayed(() -> sendEmptyLocalIceCandidate(), 100);
        }
        JSONObject json = new JSONObject();
        try {
            json.put(WebSocketConstants.HANDLE_CANDIDATE, streamName);
            json.put(WebSocketConstants.REQUEST_ID, subscribeId);

            JSONObject candidateInfo = new JSONObject();
            candidateInfo.put(WebSocketConstants.CANDIDATE, candidate.sdp);
            candidateInfo.put(WebSocketConstants.CANDIDATE_SDP_MID, candidate.sdpMid);
            candidateInfo.put(WebSocketConstants.CANDIDATE_SDP_MLINE, candidate.sdpMLineIndex);
            // TODO: ADD usernameFragment if necessary

            JSONObject candidateObj = new JSONObject();
            candidateObj.put(WebSocketConstants.CANDIDATE, candidateInfo);

            json.put(WebSocketConstants.DATA, candidateObj);
            sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendEmptyLocalIceCandidate() {
        checkIfCalledOnValidThread();
        JSONObject json = new JSONObject();
        try {
            json.put(WebSocketConstants.HANDLE_CANDIDATE, streamName);

            JSONObject candidateInfo = new JSONObject();
            candidateInfo.put(WebSocketConstants.CANDIDATE, "");
            candidateInfo.put(WebSocketConstants.TYPE, WebSocketConstants.CANDIDATE);
            // TODO: ADD usernameFragment if necessary

            JSONObject candidateObj = new JSONObject();
            candidateObj.put(WebSocketConstants.CANDIDATE, candidateInfo);

            json.put(WebSocketConstants.DATA, candidateObj);
            sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Red5MediaSignallingEvents getSignallingListener() {
        return signallingListener;
    }

    public boolean isConnected() {
        return ws.isConnected();
    }

    private String randomString(int len){
        StringBuilder sb = new StringBuilder(len);
        int alphabetNumLength = alphabetNum.length();
        for(int i = 0; i < len; i++)
            sb.append(alphabetNum.charAt(rnd.nextInt(alphabetNumLength)));
        return sb.toString();
    }
}
