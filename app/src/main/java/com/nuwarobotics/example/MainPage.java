package com.nuwarobotics.example;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainPage extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);

        // 尋找所有按鈕
        Button btnMqttConnect = findViewById(R.id.btn_mqtt_connect);
        Button btnRobotDashboard = findViewById(R.id.btn_robot_dashboard);
        Button btnNewFeature = findViewById(R.id.btn_new_feature);
        Button btnDefaultDemo = findViewById(R.id.btn_default_demo);

        // 設定點擊監聽器
        btnMqttConnect.setOnClickListener(this);
        btnRobotDashboard.setOnClickListener(this);
        btnNewFeature.setOnClickListener(this);
        btnDefaultDemo.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        int id = v.getId();
        if (id == R.id.btn_mqtt_connect) {
            // 前往 MQTT 連線頁面
            intent = new Intent(this, MqttConnectActivity.class);
            startActivity(intent);
        } else if (id == R.id.btn_robot_dashboard) {
            // 前往機器人儀錶板頁面
            intent = new Intent(this, MqttDashboardActivity.class);
            startActivity(intent);
        } else if (id == R.id.btn_new_feature) {
            // 前往新功能測試頁面 (注意：您需要建立 MqttMotorDataActivity.java 及其對應的 layout)
            // intent = new Intent(this, MqttMotorDataActivity.class);
            // startActivity(intent);
        } else if (id == R.id.btn_default_demo) {
            // 前往舊版 Defult Demo 頁面
            intent = new Intent(this, Main.class);
            startActivity(intent);
        }
    }
}
