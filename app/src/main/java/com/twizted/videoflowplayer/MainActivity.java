package com.twizted.videoflowplayer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.twizted.videoflowplayer.databinding.ActivityMainBinding;
import com.twizted.videoflowplayer.viewmodel.MainViewModel;
import com.twizted.videoflowplayer.utils.Constants;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private Handler handler;
    private Runnable mainRunnable;

    //Definition for Device Client connection
    private DeviceClient mClient;
    private final DeviceClient.DeviceRemoteServiceConnection mConnection = new DeviceClient.DeviceRemoteServiceConnection();

    public String getDeviceParameter(String parameterKeyStr) {
        String parameterValueStr;
        try {
            parameterValueStr = mClient.getDeviceParameter(parameterKeyStr,
                    "");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return parameterValueStr;
    }

    public String getDeviceSerial() {
        String parameterValueStr;
        try {
            parameterValueStr = mClient.getDeviceSerialNumber();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return parameterValueStr;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Fixes splashAnimate field in  MainViewModel is null,
        which means that your MainViewModel object itself is null.
        */

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.setContext(this);


        setContentView(R.layout.activity_main);
        //Create Device Client Connection
        mClient = new DeviceClient(getApplicationContext(), mConnection);
        try {
            mClient.connect();
            Log.d("start_deviceClient ", "starting");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.w(getClass().getSimpleName(), "TAG - onCreate");
        handler = new Handler(Looper.getMainLooper());

        // Set window styles for fullscreen-window size. Needs to be done before adding content.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsControllerCompat = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsControllerCompat.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsControllerCompat.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);

        mainRunnable = () -> {
            updateMwArgs();
            //5 sec interval to avoid he app is able to perform these updates periodically without blocking the UI thread
            handler.postDelayed(mainRunnable, 5000);
        };

        initializeObservers();
    }

    private void updateMwArgs() {
        String newMwArgs = getDeviceParameter(Constants.MW_ARGS_PATH);
        if (!viewModel.mwArgs.equals(newMwArgs)) {
            viewModel.mwArgs = newMwArgs;
            viewModel.sn = getDeviceSerial();
            Log.d("Amino H200", "Updated mwArgs: " + viewModel.mwArgs);
            viewModel.loadSystemParams(binding.videoView);
            //TODO: Live Player switch (Orchestrate) doesn't work when switching from Streaming: true to poster. Start streaming on the same player works as expected.

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.w(getClass().getSimpleName(), "TAG - onResume");
        handler.postDelayed(mainRunnable, 5000);
    }

    private void initializeObservers() {
        viewModel.splashAnimate.observe(this, aBoolean -> {
            if (aBoolean)
                binding.splashScreen.animate()
                        .alpha(0f)
                        .setDuration(1500)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                binding.splashScreen.setVisibility(View.GONE);
                            }
                        });
        });
        viewModel.stats.observe(this, s -> binding.statsOverlay.setText(s));
        viewModel.report.reconnect.observe(this, reconnectNeeded -> {
            if (reconnectNeeded) this.viewModel.onStreamButtonClick();
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.w(getClass().getSimpleName(), "TAG - onRestart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.w(getClass().getSimpleName(), "TAG - onPause");
        viewModel.onPause();
        handler.removeCallbacks(mainRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.w(getClass().getSimpleName(), "TAG - onStop");
        handler.removeCallbacks(mainRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.w(getClass().getSimpleName(), "TAG - onDestroy");
        handler.removeCallbacks(mainRunnable);
    }
}

