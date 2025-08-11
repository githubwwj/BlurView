package com.eightbitlab.blurview_sample;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ActivityBlurTest extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rect_blur);

        View viewById = findViewById(R.id.rlRoot);
        StatusBarsUtil.setEdgeToEdge(viewById);
    }
}
