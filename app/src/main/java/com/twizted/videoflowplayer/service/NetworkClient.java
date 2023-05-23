package com.twizted.videoflowplayer.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(ApplicationComponent.class)
public class NetworkClient {

    private static final String BASE_URL = "https://www.videoflow.io/";

    @Singleton
    @Provides
    public static StreamService provideStreamService() {
        final Gson gson =
                new GsonBuilder().create();
        final Retrofit retrofit = new Retrofit.Builder().baseUrl(BASE_URL)
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        return retrofit.create(StreamService.class);
    }

}
