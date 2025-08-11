package com.eightbitlab.blurview_sample;

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

        binding.blurOverlayView.setupWith(binding.blurTarget);

    }
}
