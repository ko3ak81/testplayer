package com.twizted.videoflowplayer.model;

import android.annotation.SuppressLint;
import android.icu.text.SimpleDateFormat;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.twizted.videoflowplayer.utils.Constants;


import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;

public class Report {


    private int framesReceived = 0;
    private long framesDecoded;
    private long framesDropped = 0;
    private double fps = 0;
    private String decoder;
    private long pliCount = 0;
    private long nackCount = 0;
    private long qpSum = 0;
    private long keyFramesDecoded = 0;
    private double avgInterFrameDelayMs = 0;


    private double totalInterFrameDelay = 0;
    private double totalSquaredInterFrameDelay = 0;
    private long freezeCount = 0;
    private long lastFrameRenderedTimestamp = 0;

    private long pauseCount = -1;
    private double vqi = 0;
    private double networkJitterMs = 0;




    private String trackId;
    private String mimeType;
    private long packetsReceived = 0;
    private double lastTimestamp = 0;
    private double bitrate;
    private int packetsLost;
    private long width;
    private long height;


    public MutableLiveData<Boolean> reconnect = new MutableLiveData<>();


    private final int anomalyThreshold = 10;
    private int anomaly = 0;

    private long startTime = System.nanoTime();

    private void checkForAnomalies() {
        if (anomaly > anomalyThreshold) {
            reconnect.postValue(true);
            anomaly = 0;
        } else {
            reconnect.postValue(false);
        }
    }

    private void updateFramesReceived(int frames) {
//        if(framesReceived == frames){
//            anomaly++;
//        } else {
//            anomaly = 0;
//        }
        this.framesReceived = frames;
//        checkForAnomalies();
    }

    private double avgDecodeTimeMs = 0;

    private void updateFramesDecoded(long frames, long timestamp) {
        Log.d(getClass().getSimpleName(), "Number of anomalies since the last reset: " + anomaly);

        // calculate average frame duration
        double avgFrameDurationMs = (double) totalInterFrameDelay / (framesDecoded - keyFramesDecoded);
        double freezeThresholdMs = Math.max(3 * avgFrameDurationMs, avgFrameDurationMs + 150);

        // calculate average inter-frame delay
        if (framesDecoded > keyFramesDecoded) {
            avgInterFrameDelayMs = totalInterFrameDelay / (framesDecoded - keyFramesDecoded);
        } else {
            avgInterFrameDelayMs = 0;
        }

        if (frames < (framesDecoded + (fps * 0.3))) {
            anomaly++;
        } else {
            // check for freeze
            // calculate the freeze threshold for the current frame:
        /*Count the total number of video freezes experienced by this receiver.
        It is a freeze if frame duration, which is time interval between two consecutively rendered frames,
        is equal or exceeds Max(3 * avg_frame_duration_ms, avg_frame_duration_ms + 150),
        where avg_frame_duration_ms is linear average of durations of last 30 rendered frames.*/
            int last30Frames = (int) Math.min(framesDecoded, 30);
            double totalFrameDurationMs = 0;
            for (int i = (int) framesDecoded - 1; i >= framesDecoded - last30Frames; i--) {
                double durationMs = (double) (i == keyFramesDecoded ? 0 : totalSquaredInterFrameDelay / (i - keyFramesDecoded));
                totalFrameDurationMs += durationMs;
            }
            double avgFrameDuration30Ms = totalFrameDurationMs / last30Frames;
            if (avgFrameDuration30Ms >= freezeThresholdMs) {
                if (freezeCount == 0) {
                    freezeCount++;
                    Log.d(getClass().getSimpleName(), "Frame freeze detected! avgFrameDuration30Ms=" + avgFrameDuration30Ms + ", freezeThresholdMs=" + freezeThresholdMs);
                }
            } else {
                freezeCount = 0;
            }

            // Check if time passed since last rendered frame exceeds 5 seconds
            if (timestamp - lastFrameRenderedTimestamp > 5000000000L) {
                // Increment pause count and update last rendered frame timestamp
                /*Count the total number of video pauses experienced by this receiver.
                    Video is considered to be paused if time passed since last rendered
                frame exceeds 5 seconds. pauseCount is incremented when a frame is rendered after such a pause.*/
                pauseCount++;
                lastFrameRenderedTimestamp = timestamp;
                Log.d(getClass().getSimpleName(), "Video paused! pauseCount=" + pauseCount);
            }

            // update last rendered frame timestamp
            lastFrameRenderedTimestamp = timestamp;
            anomaly = 0;
        }
        this.framesDecoded = frames;
        checkForAnomalies();
    }


    @SuppressLint("DefaultLocale")
    private String getReadableTime(Long nanos) {

        long tempSec = nanos / (1000 * 1000 * 1000);
        long sec = tempSec % 60;
        long min = (tempSec / 60) % 60;
        long hour = (tempSec / (60 * 60)) % 24;
        long day = (tempSec / (24 * 60 * 60)) % 24;

//        return String.format("%dd %dh %dm %ds", day,hour,min,sec);
        return String.format("%dd %02d:%02d:%02d", day, hour, min, sec);

    }

    public void start() {
        startTime = System.nanoTime();
    }

    private static DecimalFormat df = new DecimalFormat("0.000000");

    private String droppedReceivedRatio() {
        if (framesReceived > 0) {
            return String.format("%.2f", (float) framesDropped / framesReceived * 100);
        } else {
            return "0";
        }
    }

    private String DecodedReceivedRatio() {
        if (framesReceived > 0) {
            return String.format("%.2f", (float) framesDecoded / framesReceived * 100);
        } else {
            return "0";
        }
    }

    @SuppressLint("SimpleDateFormat")
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SS");
        return sdf.format(new Date(System.currentTimeMillis()));
    }


    public void update(Map stats) {

        Log.d(getClass().getSimpleName(), "All WebRTCStats: " + stats.toString());


        this.trackId = (String) stats.get(Constants.TRACK_ID);

        String codecId = (String) stats.get(Constants.CODEC_ID);
        if (codecId != null) {
            this.mimeType = (String) stats.get(Constants.MIME_TYPE);
        }

        long packetsReceivedNow = (long) stats.get(Constants.PACKETS_RECEIVED);
        double lastTimestampNow = (double) stats.getOrDefault(Constants.LAST_PACKET_RECEIVED_TIMESTAMP, 0.0);
        double bitrate = Math.floor(8 * (packetsReceivedNow - this.packetsReceived) / (lastTimestampNow - this.lastTimestamp));
        this.packetsReceived = packetsReceivedNow;
        this.lastTimestamp = lastTimestampNow;
        this.bitrate = bitrate;

        packetsReceived = (long) stats.get(Constants.PACKETS_RECEIVED);

        packetsLost = (int) stats.get(Constants.PACKETS_LOST);


        this.pliCount = (long) stats.getOrDefault(Constants.PLI_COUNT, 0);
        this.nackCount = (long) stats.getOrDefault(Constants.NACK_COUNT, 0);
        this.qpSum = ((Number) stats.getOrDefault(Constants.QP_SUM, 0)).longValue();
        this.keyFramesDecoded = (long) stats.getOrDefault(Constants.KEY_FRAMES_DECODED, 0);
        this.totalInterFrameDelay = (double) stats.getOrDefault(Constants.TOTAL_INTER_FRAME_DELAY, 0.0);
        this.totalSquaredInterFrameDelay = (double) stats.getOrDefault(Constants.TOTAL_SQUARED_INTER_FRAME_DELAY, 0.0);

        double frameLossRatio = (double)packetsLost / (double)packetsReceived;
        double frameDelayMs = avgInterFrameDelayMs;
        double networkDelayMs = avgInterFrameDelayMs - avgDecodeTimeMs;
        double minFrameDelayMs = 1000.0 / fps;
        double maxFrameDelayMs = Math.max(2 * minFrameDelayMs, 100.0);
        double minNetworkDelayMs = 10.0;
        double maxNetworkDelayMs = 250.0;
        vqi = (1.0 - frameLossRatio) * (1.0 - Math.min(1.0, Math.max(0.0, (frameDelayMs - minFrameDelayMs) / (maxFrameDelayMs - minFrameDelayMs)))) * (1.0 - Math.min(1.0, Math.max(0.0, (networkDelayMs - minNetworkDelayMs) / (maxNetworkDelayMs - minNetworkDelayMs))));
        networkJitterMs = Math.abs(avgInterFrameDelayMs - avgDecodeTimeMs);

        updateFramesReceived((int) stats.get(Constants.FRAMES_RECEIVED));
        updateFramesDecoded((long) stats.get(Constants.FRAMES_DECODED), System.nanoTime());

        this.framesDropped = (long) stats.get(Constants.FRAMES_DROPPED);

        this.fps = (double) stats.getOrDefault(Constants.FRAMES_PER_SECOND, 0.0);

        try {
            this.width = (long) stats.getOrDefault(Constants.FRAME_WIDTH, 0);
        } catch (Exception e) {
        }
        try {
            this.height = (long) stats.getOrDefault(Constants.FRAME_HEIGHT, 0);
        } catch (Exception e) {
        }

        this.decoder = (String) stats.getOrDefault(Constants.DECODER_IMPLEMENTATION, "N/A");
    }

    public String print() {
        StringBuilder report = new StringBuilder();
        report
                .append("Time").append(": ").append(this.getCurrentTime()).append("\n")
                .append("Track ID").append(": ").append(this.trackId).append("\n")
                .append("Mime Type").append(": ").append(this.mimeType).append("\n")
                .append("Packets Received").append(": ").append(this.packetsReceived).append("\n")
                .append("Packets Lost").append(": ").append(this.packetsLost).append("\n")
                .append("Bitrate").append(": ").append(this.bitrate).append(" kbps").append("\n")
                .append("Frames Received").append(": ").append(this.framesReceived).append("\n")
                .append("Frames Decoded").append(": ").append(this.framesDecoded).append("\n")
                .append("Frames Dropped").append(": ").append(this.framesDropped).append("\n")
                .append("Dropped/Received").append(": ").append(droppedReceivedRatio()).append("%").append("\n")

                .append("Decoded/Received").append(": ").append(DecodedReceivedRatio()).append("%").append("\n")


                .append("Freeze Count").append(": ").append(this.freezeCount).append("\n")
                .append("Pauses").append(": ").append(this.pauseCount).append("\n")
                .append("Anomalies").append(": ").append(this.anomaly).append("\n")


                .append("PLI Count").append(": ").append(this.pliCount).append("\n")
                .append("NACK Count").append(": ").append(this.nackCount).append("\n")
                /*.append("QP Sum").append(": ").append(this.qpSum).append("\n")
                .append("Key Frames Decoded").append(": ").append(this.keyFramesDecoded).append("\n")*/
                .append("Average Inter-Frame Delay").append(": ").append(String.format("%.2f", this.avgInterFrameDelayMs)).append(" ms").append("\n")
                //.append("Total Inter-Frame Delay").append(": ").append(String.format("%.2f", this.totalInterFrameDelay)).append(" Sec.").append("\n")
                //.append("Total Squared Inter-Frame Delay").append(": ").append(String.format("%.2f", this.totalSquaredInterFrameDelay)).append(" Sec.").append("\n")
                .append("VQI").append(": ").append(String.format("%.2f", this.vqi)).append("\n")
                .append("Network Jitter").append(": ").append(String.format("%.2f", this.networkJitterMs)).append(" ms").append("\n")



                .append("FPS").append(": ").append(this.fps).append("\n")
                .append("Resolution").append(": ").append(this.width).append("x").append(this.height).append("\n")
                .append("Decoder").append(": ").append(this.decoder).append("\n")
                .append("Uptime").append(": ").append(getReadableTime(System.nanoTime() - startTime)).append("\n");
        return report.toString();
    }

}