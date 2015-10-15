package com.example.xyzreader.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

/**
 * Created by bendix on 14.10.15.
 */
public class ImageVIew extends ImageView {
    private Context context;
    public ImageVIew(Context context) {
        super(context);
        this.context = context;
    }
    public ImageVIew(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }
    public ImageVIew(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }
    public void setImageURL(String url) {
        Picasso.with(context).load(url).into(this);
    }
}
