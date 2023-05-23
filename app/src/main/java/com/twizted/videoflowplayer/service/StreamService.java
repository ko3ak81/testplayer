package com.twizted.videoflowplayer.service;

import com.twizted.videoflowplayer.model.Edge;
import com.twizted.videoflowplayer.model.Stream;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

public interface StreamService {

    @GET("output/{token}/info")
    Call<Stream> getStream(@Path("token") String token,  @Query("stream") String stream);

    @GET("output/{player}/info")
    Call<Stream> getStream(@Path("player") String player,  @QueryMap Map<String, String> options);

    @GET
    Call<Edge> getEdge(@Url String url);

}
