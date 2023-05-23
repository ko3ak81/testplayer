package com.twizted.videoflowplayer.repository;

import android.util.Log;

import com.twizted.videoflowplayer.model.Edge;
import com.twizted.videoflowplayer.model.Stream;
import com.twizted.videoflowplayer.service.StreamService;

import java.util.Map;

import javax.inject.Inject;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleObserver;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Module
@InstallIn(ActivityComponent.class)
public class MainRepository {

    private final StreamService streamService;

    @Inject
    public MainRepository(StreamService streamService) {
        this.streamService = streamService;
    }


    public @NonNull Single<Edge> loadEdge(String url){
        return new Single<Edge>(){
            @Override
            protected void subscribeActual(@NonNull SingleObserver<? super Edge> observer) {
                Call<Edge> call = streamService.getEdge(url);
                call.enqueue(new Callback<Edge>(){
                    @Override
                    public void onResponse(@androidx.annotation.NonNull final Call<Edge> call, @androidx.annotation.NonNull final Response<Edge> response) {
                        if (response.isSuccessful()) {
                            observer.onSuccess(response.body());
                        } else {
                            observer.onError(new Exception());
                            Log.w(getClass().getSimpleName(), "on Response ERROR");
                        }
                    }

                    @Override
                    public void onFailure(@androidx.annotation.NonNull final Call<Edge> call, @androidx.annotation.NonNull final Throwable t) {
                        Log.w(getClass().getSimpleName(), "on Failure ERROR");
                    }
                });

            }
        };
    }


    public @NonNull Single<Stream> loadNetworkUrl(String token, Map<String, String> data) {
        return new Single<Stream>() {
            @Override
            protected void subscribeActual(@NonNull SingleObserver<? super Stream> observer) {
                Call<Stream> call = streamService.getStream(token, data);
                call.enqueue(new Callback<Stream>(){
                    @Override
                    public void onResponse(@androidx.annotation.NonNull final Call<Stream> call, @androidx.annotation.NonNull final Response<Stream> response) {
                        if (response.isSuccessful()) {
                            Stream stream = response.body();
                            if (stream != null) { // add null check
                                observer.onSuccess(stream);
                            } else {
                                observer.onError(new Exception());
                            }
                        } else {
                            observer.onError(new Exception());
                        }
                    }

                    @Override
                    public void onFailure(@androidx.annotation.NonNull final Call<Stream> call, @androidx.annotation.NonNull final Throwable t) {
                        observer.onError(t);
                    }
                });

            }
        };
    }


}
