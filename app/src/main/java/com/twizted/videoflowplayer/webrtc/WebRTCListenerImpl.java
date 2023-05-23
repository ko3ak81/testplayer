package com.twizted.videoflowplayer.webrtc;


import static androidx.core.content.ContextCompat.startActivity;
import static org.webrtc.ContextUtils.getApplicationContext;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import androidx.lifecycle.MutableLiveData;

import com.twizted.videoflowplayer.MainActivity;
import com.twizted.videoflowplayer.model.Report;
import com.twizted.videoflowplayer.utils.Constants;

import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import de.tavendo.autobahn.WebSocket;

@Module
@InstallIn(ActivityComponent.class)
public class WebRTCListenerImpl implements IWebRTCListener {

    public MutableLiveData<Boolean> splashAnimate = new MutableLiveData<>();
    public MutableLiveData<String> stats = new MutableLiveData<>();

    private long packetsReceived;
    private double lastTimestamp;
    private Report report;

    private Context context;


    @Inject
    public WebRTCListenerImpl() {
    }

    public void init(Report report) {
        this.packetsReceived = 0;
        this.lastTimestamp = 0;
        this.report = report;
        handler = new Handler();

    }

    private Handler handler = new Handler();

    private void removeCallbacksAndMessages() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    public static void releaseResources() {
        // Stop all threads
        Thread[] threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }


    public void startRestartTimer(int time) {
        handler.postDelayed(new Runnable() {


            @Override
            public void run() {
                if (WebRTCListenerImpl.this.context != null) {
                    restartApp(WebRTCListenerImpl.this.context);
                } else {
                    Log.e(getClass().getSimpleName(), "Context is null");
                    releaseResources();
                    System.exit(0);
                }
            }
        }, time);
    }




    public void restartApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(context, intent, null);
//        releaseResources();
        removeCallbacksAndMessages();
        Log.d(getClass().getSimpleName(), "Restarting app");

        if (context instanceof Activity) {
            ((Activity) context).recreate();
        }
    }

    @Override
    public void onDisconnected() {
        Log.w(getClass().getSimpleName(), "disconnected");
    }

    @Override
    public void onPublishFinished() {
        Log.w(getClass().getSimpleName(), "onPublishFinished");
    }

    @Override
    public void onPlayFinished() {
        Log.w(getClass().getSimpleName(), "onPlayFinished");
    }

    @Override
    public void onPublishStarted() {
        Log.w(getClass().getSimpleName(), "onPublishStarted");
    }

    public void onPlayStarted() {
        Log.w(getClass().getSimpleName(), "onPlayStarted");
        splashAnimate.postValue(true);
        this.context = getApplicationContext();
    }

    @Override
    public void noStreamExistsToPlay() {
        Log.w(getClass().getSimpleName(), "noStreamExistsToPlay");
    }

    @Override
    public void onError(String description) {
        Log.w(getClass().getSimpleName(), "Error: "  +description);
    }

    @Override
    public void onSignalChannelClosed(WebSocket.WebSocketConnectionObserver.WebSocketCloseNotification code) {
        Log.w(getClass().getSimpleName(), "Signal channel closed with code "  + code);
    }

    @Override
    public void onIceConnected() {
        //it is called when connected to ice
        Log.w(getClass().getSimpleName(), "onIceConnected");

        removeCallbacksAndMessages(); // Stop the timer when ice is connected
        Log.w(getClass().getSimpleName(), "Timer stopped");

    }

    @Override
    public void onIceDisconnected() {
        //it is called when disconnected to ice
        Log.w(getClass().getSimpleName(), "onIceDisconnected");

        if (context != null) {
            startRestartTimer(15000);
            //todo: return startRestartTimer(context);
        } else {
            Log.e(getClass().getSimpleName(), "Context is null");
        }

    }

    @Override
    public void onTrackList(String[] tracks) {

    }

    @Override
    public void onBitrateMeasurement(String streamId, int targetBitrate, int videoBitrate, int audioBitrate) {
        Log.e(getClass().getSimpleName(), "st:"+streamId+" tb:"+targetBitrate+" vb:"+videoBitrate+" ab:"+audioBitrate);
        if(targetBitrate < (videoBitrate+audioBitrate)) {
            Log.e(getClass().getSimpleName(), "low bandwidth");
        }
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.w(getClass().getSimpleName(), "onPeerConnectionClosed");
    }

    @Override
    public void onReport(RTCStatsReport report) {
        generateReport(report);
    }

    private void generateReport(RTCStatsReport statsReport) {
        StringBuilder report = new StringBuilder();
        Set<String> keys = statsReport.getStatsMap().keySet();

        for (Iterator it = keys.iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            RTCStats stats = statsReport.getStatsMap().get(key);
            if (stats.getType().equals(Constants.TYPE)) {
                Map members = stats.getMembers();
                if (members.get(Constants.KIND).equals(Constants.VIDEO)) {
//                    addStatValue(report, Constants.TRACK_ID, members);
                    String codecId = (String) members.get(Constants.CODEC_ID);
                    if (codecId != null) {
                        members.put(Constants.MIME_TYPE, statsReport.getStatsMap().get(codecId).getMembers().get(Constants.MIME_TYPE));
//                        addStatValue(report, Constants.MIME_TYPE, statsReport.getStatsMap().get(codecId).getMembers());
                    }
//                    long packetsReceivedNow = (long) members.get(Constants.PACKETS_RECEIVED);
//                    double lastTimestampNow = (double) members.getOrDefault(Constants.LAST_PACKET_RECEIVED_TIMESTAMP, 0.0);
//                    double bitrate = Math.floor(8 * (packetsReceivedNow - packetsReceived) / (lastTimestampNow - lastTimestamp));
//                    packetsReceived = packetsReceivedNow;
//                    lastTimestamp = lastTimestampNow;
//                    report.append("Bitrate: ").append(bitrate).append(" kbits/sec\n");
//                    addStatValue(report, Constants.PACKETS_RECEIVED, members);
//                    addStatValue(report, Constants.PACKETS_LOST, members);
//                    addStatValue(report, Constants.FRAMES_RECEIVED, members);
//                    addStatValue(report, Constants.FRAMES_DECODED, members);
//                    addStatValue(report, Constants.FRAMES_DROPPED, members);
//                    addStatValue(report, Constants.FRAMES_PER_SECOND, members);
//                    report.append("Resolution: ")
//                            .append(members.get(Constants.FRAME_WIDTH))
//                            .append(" x ")
//                            .append(members.get(Constants.FRAME_HEIGHT))
//                            .append("\n");
//                    addStatValue(report, Constants.DECODER_IMPLEMENTATION, members);
                    try{
                        this.report.update(members);
                    } catch (Exception e){
                        Log.e(getClass().getSimpleName(), e.toString());
                    }

                }
            }
        }
//        stats.postValue(report.toString() + "\n" + this.report.print());
        stats.postValue(this.report.print());
    }

//    private void addStatValue(StringBuilder builder, String key, Map<String, Object> members) {
//        builder.append(key).append(": ").append(members.get(key)).append("\n");
//    }
}
