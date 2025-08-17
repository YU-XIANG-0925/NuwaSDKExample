package com.nuwarobotics.example;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nuwarobotics.service.IClientId;
import com.nuwarobotics.service.agent.NuwaRobotAPI;
import com.nuwarobotics.service.agent.RobotEventListener;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MqttDashboardActivity extends AppCompatActivity {

    private final String TAG = "MqttDashboardActivity";

    // MQTT 連線資訊
    private String serverUri;
    private String clientId;
    private String topic;

    // UI 元件
    private TextView textViewLog;
    private Map<String, TextView> motorTextViews = new HashMap<>();

    // Nuwa SDK API
    private NuwaRobotAPI mRobotAPI;
    private IClientId mIClientId;
    private boolean isNuwaApiReady = false;

    // MQTT Client
    private MqttAndroidClient mqttClient;

    // Gson for JSON parsing
    private Gson gson = new Gson();

    // 用於 Gson 解析 JSON 陣列的內部類別
    private static class MotorCommand {
        String motorId;
        float angle;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqtt_dashboard);

        // 1. 從 Intent 中獲取連線資訊
        Intent intent = getIntent();
        serverUri = intent.getStringExtra("SERVER_URI");
        clientId = intent.getStringExtra("CLIENT_ID");
        topic = intent.getStringExtra("TOPIC");

        // 2. 綁定 UI 元件
        bindViews();

        // 3. 初始化 Nuwa SDK (非同步)
        initNuwaSdk();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 釋放資源
        if (mRobotAPI != null) {
            mRobotAPI.release();
        }
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                // 取消訂閱並斷開連線
                mqttClient.unsubscribe(topic);
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private void initNuwaSdk() {
        if (isEmulator()) {
            Log.d(TAG, "模擬器模式：跳過 Nuwa SDK 初始化，直接啟動 MQTT。");
            isNuwaApiReady = true;
            initMqttClient();
        } else {
            Log.d(TAG, "實體裝置：正在初始化 Nuwa SDK...");
            mIClientId = new IClientId(this.getPackageName());
            mRobotAPI = new NuwaRobotAPI(this, mIClientId);
            mRobotAPI.registerRobotEventListener(robotEventListener);
        }
    }

    private void initMqttClient() {
        logMessage("Nuwa SDK 準備完成，正在初始化 MQTT Client...");
        mqttClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // 設定 MQTT 的回呼 (Callback)
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // 這個方法會在連線成功或重連成功時被呼叫
                logMessage("MQTT 連線完成，準備訂閱主題...");
                subscribeToTopic();
            }

            @Override
            public void connectionLost(Throwable cause) {
                logMessage("MQTT 連線中斷！");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // *** 核心邏輯：當收到訊息時，這個方法會被觸發 ***
                String payload = new String(message.getPayload());
                logMessage("收到訊息 (" + topic + "): " + payload);
                // 處理收到的馬達指令
                handleMotorCommand(payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 這個方法在訊息發送成功時被呼叫，我們目前用不到
            }
        });

        // 設定連線選項
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setAutomaticReconnect(true); // 啟用自動重連
        connectOptions.setCleanSession(true);

        // 連線到 Broker
        try {
            mqttClient.connect(connectOptions);
        } catch (MqttException e) {
            e.printStackTrace();
            logMessage("MQTT 連線失敗: " + e.getMessage());
        }
    }

    private void subscribeToTopic() {
        try {
            mqttClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    logMessage("成功訂閱主題: " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    logMessage("訂閱主題 " + topic + " 失敗！");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void handleMotorCommand(String jsonPayload) {
        try {
            // 使用 Gson 解析 JSON 陣列
            Type listType = new TypeToken<List<MotorCommand>>() {}.getType();
            List<MotorCommand> commands = gson.fromJson(jsonPayload, listType);

            if (commands == null || commands.isEmpty()) {
                logMessage("收到的指令為空或格式不符。");
                return;
            }

            for (MotorCommand command : commands) {
                // 更新 UI 上的角度
                updateMotorAngle(command.motorId, command.angle);

                // 控制實際的機器人馬達 (在模擬器上會跳過)
                if (isNuwaApiReady && mRobotAPI != null && !isEmulator()) {
                    int motorId = getMotorIdFromString(command.motorId);
                    if (motorId != -1) {
                        logMessage("控制馬達: " + command.motorId + " -> " + command.angle);
                        mRobotAPI.ctlMotor(motorId, command.angle, 45); // 使用預設速度 45
                    } else {
                        logMessage("警告：找不到馬達 ID '" + command.motorId + "'");
                    }
                }
            }
        } catch (Exception e) {
            logMessage("JSON 解析錯誤: " + e.getMessage());
        }
    }

    private int getMotorIdFromString(String motorName) {
        if (motorName == null) return -1;
        switch (motorName) {
            case "NECK_Y": return NuwaRobotAPI.MOTOR_NECK_Y;
            case "NECK_Z": return NuwaRobotAPI.MOTOR_NECK_Z;
            case "RIGHT_SHOULDER_Z": return NuwaRobotAPI.MOTOR_RIGHT_SHOULDER_Z;
            case "RIGHT_SHOULDER_Y": return NuwaRobotAPI.MOTOR_RIGHT_SHOULDER_Y;
            case "RIGHT_SHOULDER_X": return NuwaRobotAPI.MOTOR_RIGHT_SHOULDER_X;
            case "RIGHT_ELBOW_Y": return NuwaRobotAPI.MOTOR_RIGHT_ELBOW_Y;
            case "LEFT_SHOULDER_Z": return NuwaRobotAPI.MOTOR_LEFT_SHOULDER_Z;
            case "LEFT_SHOULDER_Y": return NuwaRobotAPI.MOTOR_LEFT_SHOULDER_Y;
            case "LEFT_SHOULDER_X": return NuwaRobotAPI.MOTOR_LEFT_SHOULDER_X;
            case "LEFT_ELBOW_Y": return NuwaRobotAPI.MOTOR_LEFT_ELBOW_Y;
            default: return -1; // 找不到對應的馬達
        }
    }

    private void bindViews() {
        textViewLog = findViewById(R.id.textView_log);
        textViewLog.setText(""); // 清空預設文字

        motorTextViews.put("NECK_Y", (TextView) findViewById(R.id.textView_neck_y));
        motorTextViews.put("NECK_Z", (TextView) findViewById(R.id.textView_neck_z));
        motorTextViews.put("RIGHT_SHOULDER_Z", (TextView) findViewById(R.id.textView_r_shoulder_z));
        motorTextViews.put("LEFT_SHOULDER_Z", (TextView) findViewById(R.id.textView_l_shoulder_z));
        motorTextViews.put("RIGHT_SHOULDER_Y", (TextView) findViewById(R.id.textView_r_shoulder_y));
        motorTextViews.put("LEFT_SHOULDER_Y", (TextView) findViewById(R.id.textView_l_shoulder_y));
        motorTextViews.put("RIGHT_SHOULDER_X", (TextView) findViewById(R.id.textView_r_shoulder_x));
        motorTextViews.put("LEFT_SHOULDER_X", (TextView) findViewById(R.id.textView_l_shoulder_x));
        motorTextViews.put("RIGHT_ELBOW_Y", (TextView) findViewById(R.id.textView_r_elbow_y));
        motorTextViews.put("LEFT_ELBOW_Y", (TextView) findViewById(R.id.textView_l_elbow_y));
    }

    private void logMessage(final String message) {
        runOnUiThread(() -> {
            Log.d(TAG, message);
            if (textViewLog != null) {
                textViewLog.append(message + "\n");
            }
        });
    }

    private void updateMotorAngle(String motorId, float angle) {
        if (motorId == null) return;
        TextView targetTextView = motorTextViews.get(motorId);
        if (targetTextView != null) {
            runOnUiThread(() -> targetTextView.setText(String.valueOf(angle)));
        }
    }

    private RobotEventListener robotEventListener = new RobotEventListener() {
        @Override
        public void onWikiServiceStart() {
            isNuwaApiReady = true;
            // 只有在 Nuwa SDK 準備好後，才開始進行 MQTT 連線
            initMqttClient();
        }
        @Override
        public void onWikiServiceStop() { isNuwaApiReady = false; }
        // ... 其他空白的回呼方法 ...
        @Override
        public void onWikiServiceCrash() {}
        @Override
        public void onWikiServiceRecovery() {}
        @Override
        public void onStartOfMotionPlay(String s) {}
        @Override
        public void onPauseOfMotionPlay(String s) {}
        @Override
        public void onStopOfMotionPlay(String s) {}
        @Override
        public void onCompleteOfMotionPlay(String s) {}
        @Override
        public void onPlayBackOfMotionPlay(String s) {}
        @Override
        public void onErrorOfMotionPlay(int i) {}
        @Override
        public void onPrepareMotion(boolean b, String s, float v) {}
        @Override
        public void onCameraOfMotionPlay(String s) {}
        @Override
        public void onGetCameraPose(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8, float v9, float v10, float v11) {}
        @Override
        public void onTouchEvent(int i, int i1) {}
        @Override
        public void onPIREvent(int i) {}
        @Override
        public void onTap(int i) {}
        @Override
        public void onLongPress(int i) {}
        @Override
        public void onWindowSurfaceReady() {}
        @Override
        public void onWindowSurfaceDestroy() {}
        @Override
        public void onTouchEyes(int i, int i1) {}
        @Override
        public void onRawTouch(int i, int i1, int i2) {}
        @Override
        public void onFaceSpeaker(float v) {}
        @Override
        public void onActionEvent(int i, int i1) {}
        @Override
        public void onDropSensorEvent(int i) {}
        @Override
        public void onMotorErrorEvent(int i, int i1) {}
    };

    private boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }
}
