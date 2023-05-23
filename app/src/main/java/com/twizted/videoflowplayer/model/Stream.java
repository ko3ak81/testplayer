package com.twizted.videoflowplayer.model;

import lombok.Getter;
import lombok.Setter;

public class Stream {

    @Setter
    private String name;
    @Setter
    @Getter
    private String url;
    @Setter
    @Getter
    private String poster;
    @Setter
    @Getter
    private boolean debug = false;

    public String getDomain() {
        String[] splitedUrl = url.split("//");
        return splitedUrl[1].split("/")[0];
    }

    public String getApp() {
        String[] splitedUrl = url.split("//");
        return splitedUrl[1].split("/")[1];
    }

    public String getStreamName() {
        String[] splitedUrl = url.split("//");
        return splitedUrl[1].split("/")[2];
    }

}
