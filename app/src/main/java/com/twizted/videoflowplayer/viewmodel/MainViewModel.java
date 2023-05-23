package com.twizted.videoflowplayer.viewmodel;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.twizted.videoflowplayer.R;
import com.twizted.videoflowplayer.model.Report;
import com.twizted.videoflowplayer.model.Stream;
import com.twizted.videoflowplayer.repository.MainRepository;
import com.twizted.videoflowplayer.utils.Constants;
import com.twizted.videoflowplayer.webrtc.WebRTCListenerImpl;
import com.twizted.videoflowplayer.webrtc.WebRTCNativeClient;

import org.webrtc.SurfaceViewRenderer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dagger.hilt.android.qualifiers.ActivityContext;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class MainViewModel extends ViewModel {


    private final String wss = "wss://";
    private String serverUrl = "";



    private WebRTCNativeClient webRTCClient;
    private WeakReference<SurfaceViewRenderer> renderer;

    private final Runnable streamRunnable = this::onStreamButtonClick;
    private final Handler handler;
    private final ScheduledThreadPoolExecutor executor;
    private ScheduledFuture schedulerFuture;
    private ScheduledFuture networkFuture;

    private String streamId = "stream1";
    private String token = "";
    private Integer delay = 8000;
    private boolean androidKey = false;
    private String player = "";
    private String stream = "";
    private String streamProp = "";
    public String sn = "";


    private final MainRepository repository;
    private final WeakReference<Context> context;
    private WebRTCListenerImpl webRTCListener;
    private CompositeDisposable compositeDisposable;

    public MutableLiveData<String> poster = new MutableLiveData<>();
    public MutableLiveData<String> uniqueKey = new MutableLiveData<>();
    public MutableLiveData<Boolean> debug = new MutableLiveData<>();
    public LiveData<Boolean> splashAnimate;
    public String mwArgs = "";
    public LiveData<String> stats;
    public Report report;

//    String mw_args = MainActivity.data;

    @ViewModelInject
    public MainViewModel(MainRepository repository, WebRTCListenerImpl webRTCListener, @ActivityContext Context context) {
        this.repository = repository;
        this.webRTCListener = webRTCListener;
        this.report = new Report();
        this.context = new WeakReference<>(context);
        this.executor = new ScheduledThreadPoolExecutor(2);
        this.handler = new Handler(Looper.getMainLooper());
        this.compositeDisposable = new CompositeDisposable();
        this.debug.postValue(false);
        this.splashAnimate = Transformations.switchMap(webRTCListener.splashAnimate, MutableLiveData::new);
        this.stats = Transformations.switchMap(webRTCListener.stats, MutableLiveData::new);

        //*** this is what causing the stats not to show!!!***
        //this.webRTCListener = new WebRTCListenerImpl();

    }

    public void loadSystemParams(SurfaceViewRenderer renderer) {
        this.renderer = new WeakReference<>(renderer);
        try {
            if (mwArgs != null && !mwArgs.isEmpty()) {
                String[] dataSplit = mwArgs.split("&");
                token = getValueForKey(dataSplit, Constants.TOKEN, "");
                player = getValueForKey(dataSplit, Constants.PLAYER, Constants.DEFAULT_PLAYER);
                stream = getValueForKey(dataSplit, Constants.STREAM, "");
                streamProp = getValueForKey(dataSplit, Constants.STREAM_PROP, Constants.DEFAULT_STREAM_PROP);

                try{
                    delay = Integer.parseInt(getValueForKey(dataSplit, Constants.DELAY, "0"));
                } catch (Exception e){
                    Log.e("TAG", "Error parsing delay value: " + e.getMessage());
                }
                debug.postValue(Boolean.parseBoolean(getValueForKey(dataSplit, Constants.DEBUG, "false")));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        loadNetworkUrl();

        // If Engage token is empty, use unique device id to contact server
//        if (token.isEmpty()) {
//            getUniqueKey();
//        } else {
//            loadNetworkUrl(stream);
//        }

        // Removing fallback stream
//        createWebRTC();
//        startScheduler();
    }

    private void getUniqueKey() {
        token = Settings.Secure.getString(context.get().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        androidKey = true;
        Log.w(getClass().getSimpleName(), "Android unique key - " + token);
        loadNetworkUrl();
    }

    private String getValueForKey(String[] data, String key, String defaultVal) {
        for (String child : data) {
            String[] obj = child.split("=");
            if (obj.length == 2 && obj[0].equals(key))
                return obj[1];
        }
        return  defaultVal;
    }

    private void setPoster(Stream stream) {
        Context context = this.context.get();

        if (context == null) {
            Log.e("setPoster", "No context");
            return;
        }

        if (!isInternetConnected()) {
            Log.d("setPoster", "No internet connection.");
            poster.postValue(loadLocalPoster());
            webRTCListener.startRestartTimer(10000);
            return;
        }

        if (stream != null && stream.getPoster() != null && !stream.getPoster().isEmpty()) {
            Log.e("setPoster", "Setting poster from stream");
            poster.postValue(stream.getPoster());
        } else {
            poster.postValue("https://videoflow-resources.s3.us-west-2.amazonaws.com/videoflow-icon-splash.jpg");
        }
    }



    private boolean isInternetConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    private String loadLocalPoster() {
        Context context = this.context.get();
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("videoflowiconsplash", "mipmap", context.getPackageName());
        if (resourceId != 0) {
            return "android.resource://" + context.getPackageName() + "/" + resourceId;
        } else {

            // Return default poster image if local resource does not exist
            return "https://videoflow-resources.s3.us-west-2.amazonaws.com/videoflow-icon-splash.jpg";
        }
    }

    private WeakReference<Context> weakContext;

    public void setContext(Context context) {
        weakContext = new WeakReference<>(context);
    }


    private void loadStreamFromUrl(String url, String id, Stream stream){
        serverUrl = url;
        streamId = id;
        setPoster(stream);
        if(!debug.getValue()){
            debug.postValue(stream.isDebug());
        }

        createWebRTC();
        startScheduler();
    }

    private void loadNetworkUrl() {

        Map<String, String> data = new HashMap<>();
        data.put(streamProp, stream);

        compositeDisposable.add(repository.loadNetworkUrl(player, data).subscribe((streamObj, throwable) -> {

            if (streamObj != null) {

                if(streamObj.getUrl().indexOf("streammanager") > -1){
                    compositeDisposable.add(repository.loadEdge(streamObj.getUrl()).subscribe((edgeObj, throwable1) -> {
                        if (edgeObj != null) {
                            loadStreamFromUrl(wss + streamObj.getDomain() + "/" + streamObj.getApp() + "/?host=" + edgeObj.getServerAddress()+ "&app=" + edgeObj.getScope(), edgeObj.getName(), streamObj);
                        } else {
                            // handle null edgeObj here
                            Log.d(getClass().getSimpleName(),"edgeObj is null");
                            //The line below is commented out to start stream from poster when become True
//                            loadStreamFromUrl(wss + streamObj.getDomain() + "/" + streamObj.getApp() + "/", streamObj.getStreamName(), streamObj);
                            setPoster(streamObj);
                            new Handler().postDelayed(() -> loadNetworkUrl(), 1000);

                        }
                    }));
                } else {
                    loadStreamFromUrl(wss + streamObj.getDomain() + "/" + streamObj.getApp() + "/", streamObj.getStreamName(), streamObj);
                }



            } else if (throwable != null) {
                setPoster(streamObj);
                new Handler().postDelayed(() -> loadNetworkUrl(), 1000);

//                poster.postValue("https://videoflow-resources.s3.us-west-2.amazonaws.com/videoflow-icon-splash.jpg");
                // If we get an error from the API server, check if we sent the
                // token from Engage or unique key.
                // If we tried with token, try again with unique key or else show
                // unique key to user.
//                if (!androidKey) {
//                    getUniqueKey();
//                } else {
//                    showUniqueKey();
//                }
            }
        }));
    }

    private void showUniqueKey() {
        uniqueKey.postValue(context.get().getString(R.string.unique_app_id, sn));
    }

    private void createWebRTC() {
        if (webRTCClient == null) {
            webRTCListener.init(this.report);
            webRTCClient = new WebRTCNativeClient(webRTCListener, context.get());
            webRTCClient.setRenderer(renderer.get());
            webRTCClient.init(serverUrl, streamId, debug.getValue());
        }

        onStreamButtonClick();
    }

    private void  startScheduler() {
        schedulerFuture =  executor.scheduleWithFixedDelay(() -> {
            Log.w(getClass().getSimpleName(), "Stream runnable:" + webRTCClient + " - streaming:" + webRTCClient.isStreaming());
            if (webRTCClient != null && !webRTCClient.isStreaming()) {
                handler.post(streamRunnable);
            }
        }, 5, 2,  TimeUnit.SECONDS);
        networkFuture = executor.scheduleWithFixedDelay(() -> {
            Log.w(getClass().getSimpleName(), "Videoflow network call");
            if (token.isEmpty()) return;
            Map<String, String> data = new HashMap<>();
            data.put(streamProp, stream);
            compositeDisposable.add(
                    repository.loadNetworkUrl(player, data)
                            .subscribe((streamObj, throwable) -> {
                                if (streamObj != null && !streamObj.getStreamName().equals(streamId)) {
                                    // stop and release webrtc because we got new stream value
                                    webRTCClient.stopStream();
                                    // Create url from server obj
                                    serverUrl = wss + streamObj.getDomain() + "/" + streamObj.getApp() + "/";
//                                    streamId
                                    streamId = streamObj.getStreamName();
                                    if(!debug.getValue()){
                                        debug.postValue(streamObj.isDebug());
                                    }

                                    createWebRTC();
                                }
                            })
            );
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void onStreamButtonClick() {
        if (!webRTCClient.isStreaming()) {
            Log.w(getClass().getSimpleName(), "STREAM START");
            webRTCClient.startStream(serverUrl, streamId, debug.getValue());
            report.start();
        }
        else {
            Log.w(getClass().getSimpleName(), "STREAM STOP");
            webRTCClient.stopStream();
        }
    }

    public void onPause() {
        compositeDisposable.dispose();
        if (schedulerFuture != null) schedulerFuture.cancel(false);
        if (networkFuture != null) networkFuture.cancel(true);
        if (webRTCClient != null) webRTCClient.stopStream();
        webRTCClient = null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.dispose();
    }
}
