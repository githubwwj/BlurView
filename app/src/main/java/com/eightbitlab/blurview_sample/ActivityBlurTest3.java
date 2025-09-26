package com.eightbitlab.blurview_sample;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.eightbitlab.blurview_sample.databinding.ActivityRectBlur3Binding;
import com.eightbitlab.blurview_sample.databinding.ActivityRectBlurBinding;

public class ActivityBlurTest3 extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRectBlur3Binding binding = ActivityRectBlur3Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        StatusBarsUtil.setEdgeToEdge(binding.getRoot());
        Drawable drawable = binding.ivImg.getDrawable();
        if (!(drawable instanceof BitmapDrawable) || (drawable.getIntrinsicWidth() <= 0)) {
            return;
        }
        binding.blurView.setupWith(binding.ivImg).setBlurRadius(24);


    }
}
