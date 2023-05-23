package com.twizted.videoflowplayer.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class WebRTCNativeClient implements PeerConnection.Observer, Red5MediaSignallingEvents{
    private static final String TAG = "WebRTCNativeClient";

    private Handler handler = new Handler(Looper.getMainLooper());

    private Context context;
    private final IWebRTCListener webRTCListener;

    private String stunServerUri = "stun:stun.l.google.com:19302";
    private List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    private PeerConnection peerConnection;
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase rootEglBase;
    private SurfaceViewRenderer renderer;
    private MediaConstraints sdpMediaConstraints;
    private boolean iceConnected;

    private WebSocketHandler wsHandler;
    private Timer statsTimer;

    private String streamId;
    private boolean debug;
    private String url;

    public WebRTCNativeClient(IWebRTCListener webRTCListener, Context context) {
        this.webRTCListener = webRTCListener;
        this.context = context;
    }

    public void setRenderer(SurfaceViewRenderer renderer) {
        this.renderer = renderer;
    }

    public void init(String url, String streamId, boolean debug) {
        if (peerConnection != null) {
            Log.w(TAG, "There is already a active peerconnection client ");
            return;
        }

        //Uri roomUri = this.activity.getIntent().getData();
        if (url == null) {
            Log.e(TAG, "Didn't get any URL in intent!");
            return;
        }
        this.url = url;

        if (streamId == null || streamId.length() == 0) {
            Log.e(TAG, "Incorrect room ID in intent!");
            return;
        }

        this.streamId = streamId;
        this.debug = debug;
        iceConnected = false;

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"));

        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(stunServerUri).createIceServer();
        peerIceServers.add(peerIceServer);

        rootEglBase = EglBase.create();
        renderer.init(rootEglBase.getEglBaseContext(), null);
        renderer.setZOrderMediaOverlay(true);
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
        createPeerConnection();
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, this);
    }

    public void startStream(String url, String streamId, boolean debug) {
        init(url, streamId, debug);
        if (wsHandler == null) {
            wsHandler = new WebSocketHandler(this, handler, this.streamId);
            wsHandler.connect(url);
        } else if (!wsHandler.isConnected()) {
            wsHandler.disconnect(true);
            wsHandler = new WebSocketHandler(this, handler, this.streamId);
            wsHandler.connect(url);
        }
        wsHandler.startPlay();
    }

    public void stopStream() {
        disconnect();
    }

    public void disconnect() {
        release();
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void release() {
        iceConnected = false;
        cancelTimer();
        if (wsHandler != null && wsHandler.getSignallingListener().equals(this)) {
            wsHandler.disconnect(true);
            wsHandler = null;
        }

        if (renderer != null) {
            renderer.release();
            // renderer = null; Do not make renderer null, we can re-use
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
    }

    private void cancelTimer() {
        // Stop the timer if it's running
        if (statsTimer != null) {
            statsTimer.cancel();
            statsTimer = null;
        }
    }

    public boolean isStreaming() {
        return iceConnected;
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
//        runOnUiThread(() -> {
            try {
                videoTrack.addSink(renderer);
            } catch (Exception e) {
                e.printStackTrace();
            }
//        });
    }

    /**
     * PeerConnection observer methods
     */

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(TAG, "onSignalingChange() called with: signalingState = [" + signalingState + "]");
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(TAG, "onIceConnectionChange() called with: iceConnectionState = [" + iceConnectionState + "]");
        this.handler.post(() -> {
            if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                iceConnected = true;
                enableStatsEvents(debug, 1000);
                if (webRTCListener != null) {
                    webRTCListener.onIceConnected();
                }
            } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                iceConnected = false;
                disconnect();
                if (webRTCListener != null) {
                    webRTCListener.onIceDisconnected();
                }
            } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                iceConnected = false;
                disconnect();
                if (webRTCListener != null) {
                    webRTCListener.onError("ICE connection failed.");
                }
            }
        });

    }

    public void enableStatsEvents(boolean enable, int periodMs) {
        Log.d(TAG, "enableStatsEvents() called with: enabled = [" + enable + "]  --- [" + handler + "]  --- [" + peerConnection + "]  --- [" + webRTCListener + "]");
        if (true) {
            try {
                if (statsTimer == null) statsTimer = new Timer();
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(() -> {
                            if (peerConnection == null) return;
                            peerConnection.getStats(rtcStatsReport -> {
                                if (webRTCListener != null)
                                    webRTCListener.onReport(rtcStatsReport);
                            });
                        });
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(TAG, "Can not schedule statistics timer", e);
            }
        } else {
            cancelTimer();
        }
    }

    @Override
    public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
        Log.d(TAG, "onStandardizedIceConnectionChange() called with: newState = [" + newState + "]");
    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        Log.d(TAG, "onConnectionChange() called with: newState = [" + newState + "]");
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        Log.d(TAG, "onIceConnectionReceivingChange() called with: b = [" + b + "]");
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "onIceGatheringChange() called with: iceGatheringState = [" + iceGatheringState + "]");
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        Log.d(TAG, "onIceCandidate() called with: iceCandidate = [" + iceCandidate + "]");
        this.handler.post(() -> {
            if (wsHandler != null) wsHandler.sendLocalIceCandidate(iceCandidate);
        });

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        Log.d(TAG, "onIceCandidatesRemoved() called with: iceCandidates = [" + iceCandidates + "]");
    }

    @Override
    public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
        Log.d(TAG, "onSelectedCandidatePairChanged() called with: event = [" + event + "]");
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(TAG, "onAddStream() called with: mediaStream = [" + mediaStream + "]");
        gotRemoteStream(mediaStream);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(TAG, "onRemoveStream() called with: mediaStream = [" + mediaStream + "]");
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.d(TAG, "onDataChannel() called with: dataChannel = [" + dataChannel + "]");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded() called");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d(TAG, "onAddTrack() called with: rtpReceiver = [" + rtpReceiver + "]  -- mediaStreams = [" + mediaStreams + "]");
    }

    @Override
    public void onTrack(RtpTransceiver transceiver) {
        Log.d(TAG, "onTrack() called with: transceiver = [" + transceiver + "]");
    }


    /**
     * Red5Pro signaling server methods
     */

    @Override
    public void onRemoteIceCandidate(String streamId, IceCandidate candidate) {
        this.handler.post(() -> {
            if (peerConnection == null) {
                Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                return;
            }
            peerConnection.addIceCandidate(candidate);
        });
    }

    @Override
    public void onTakeConfiguration(String streamId, SessionDescription sdp) {
        this.handler.post(() -> {
            if (sdp.type == SessionDescription.Type.OFFER) {
                peerConnection.setRemoteDescription(new CustomSdpObserver("remoteDesc"), sdp);

                peerConnection.createAnswer(new CustomSdpObserver("createAnswer") {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        peerConnection.setLocalDescription(new CustomSdpObserver("setLocalDescription"), sessionDescription);
                        handler.post(() -> wsHandler.sendConfiguration(sessionDescription, WebSocketConstants.ANSWER));
                    }
                }, sdpMediaConstraints);
            }
        });
    }

    @Override
    public void onPlayStarted(String streamId) {
        this.handler.post(() -> {
            if (webRTCListener != null) {
                webRTCListener.onPlayStarted();
            }
        });
    }

    @Override
    public void onPlayFinished(String streamId) {
        this.handler.post(() -> {
            if (webRTCListener != null) {
                webRTCListener.onPlayFinished();
            }
            disconnect();
        });
    }

    @Override
    public void noStreamExistsToPlay(String streamId) {
        this.handler.post(() -> {
            if (webRTCListener != null) {
                webRTCListener.noStreamExistsToPlay();
            }
        });
    }

    @Override
    public void onStreamLeaved(String streamId) {

    }

    @Override
    public void onBitrateMeasurement(String streamId, int targetBitrate, int videoBitrate, int audioBitrate) {

    }
}
