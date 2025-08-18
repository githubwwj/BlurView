package com.eightbitlab.blurview_sample;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.eightbitlab.blurview_sample.databinding.ActivityRectBlurBinding;

public class ActivityBlurTest extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRectBlurBinding binding = ActivityRectBlurBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        StatusBarsUtil.setEdgeToEdge(binding.getRoot());

//        binding.blurOverlayView.setupWith(binding.blurTarget, 20f);

        Drawable drawable = binding.ivImg.getDrawable();
        if (!(drawable instanceof BitmapDrawable) || (drawable.getIntrinsicWidth() <= 0)) {
            return;
        }
        binding.blurOverlayView.setupWith(binding.blurTarget, 21);


    }
}
