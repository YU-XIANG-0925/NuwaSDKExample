package com.nuwarobotics.example.motion.base;

import android.os.Bundle;
//import android.support.v7.app.ActionBar;
//import android.support.v7.app.AppCompatActivity;
//import android.support.v7.widget.Toolbar;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nuwarobotics.example.R;

import ads_mobile_sdk.v7;

public class BaseAppCompatActivity extends AppCompatActivity {
    protected final String TAG = "NuwaSDKMotion";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutRes());
        initBaseUI();
    }


    private void initBaseUI(){
        Toolbar toolbar = findViewById(R.id.common_action_bar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getToolBarTitleRes());
        }

    }

    protected int getLayoutRes(){
        return R.layout.activity_main_motion_sdk;
    }

    protected int getToolBarTitleRes(){
        return R.string.motion_sdk_example_title;
    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return true;
    }

}




