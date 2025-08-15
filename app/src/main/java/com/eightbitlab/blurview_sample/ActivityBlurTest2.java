package com.eightbitlab.blurview_sample;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.eightbitlab.blurview_sample.databinding.ActivityRectBlur2Binding;
import com.eightbitlab.blurview_sample.databinding.ActivityRectBlurBinding;

public class ActivityBlurTest2 extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRectBlur2Binding binding = ActivityRectBlur2Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        StatusBarsUtil.setEdgeToEdge(binding.getRoot());

        binding.blurRl.setupWith(binding.blurTarget, 20);
    }
}
