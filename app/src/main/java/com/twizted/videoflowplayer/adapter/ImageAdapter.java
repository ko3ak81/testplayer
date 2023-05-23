package com.twizted.videoflowplayer.adapter;

import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

import com.squareup.picasso.Picasso;

public class ImageAdapter {

    @BindingAdapter(value={"imageUrl"}, requireAll=false)
    public static void setImageUrl(ImageView imageView, String url) {
        if (url == null || url.isEmpty()) {
            imageView.setImageDrawable(null);
        } else {
            Picasso.get().load(url).into(imageView);
        }
    }

}
