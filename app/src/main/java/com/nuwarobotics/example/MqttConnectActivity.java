package com.nuwarobotics.example;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.nuwarobotics.service.IClientId;
import com.nuwarobotics.service.agent.NuwaRobotAPI;
import com.nuwarobotics.service.agent.RobotEventListener;

// 引入 Paho MQTT 相關類別
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MqttConnectActivity extends AppCompatActivity {

    private final String TAG = "MqttConnectActivity";

    // UI 元件
    private EditText editTextBroker;
    private EditText editTextPort;
    private EditText editTextClientId;
    private EditText editTextTopic;
    private Button buttonConnect;
    private TextView textViewStatus;

    // Nuwa SDK API
    private NuwaRobotAPI mRobotAPI;
    private IClientId mClientId;
    private boolean isNuwaApiReady = false;

    // MQTT Client
    private MqttAndroidClient mqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqtt_connect);

        editTextBroker = findViewById(R.id.editText_broker);
        editTextPort = findViewById(R.id.editText_port);
        editTextClientId = findViewById(R.id.editText_clientId);
        editTextTopic = findViewById(R.id.editText_topic);
        buttonConnect = findViewById(R.id.button_connect);
        textViewStatus = findViewById(R.id.textView_status);

        buttonConnect.setEnabled(false);
        buttonConnect.setText("Nuwa SDK 初始化中...");

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNuwaApiReady) {
                    connectToMqtt();
                } else {
                    Toast.makeText(MqttConnectActivity.this, "Nuwa SDK 正在初始化，請稍候...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (isEmulator()) {
            Log.d(TAG, "偵測到模擬器環境，進入開發模式。");
            isNuwaApiReady = true;
            buttonConnect.setText("連線 (模擬模式)");
            buttonConnect.setEnabled(true);
        } else {
            Log.d(TAG, "偵測到實體裝置，初始化 Nuwa SDK。");
            mClientId = new IClientId(this.getPackageName());
            mRobotAPI = new NuwaRobotAPI(this, mClientId);
            mRobotAPI.registerRobotEventListener(robotEventListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRobotAPI != null) {
            mRobotAPI.release();
        }
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private RobotEventListener robotEventListener = new RobotEventListener() {
        @Override
        public void onWikiServiceStart() {
            Log.d(TAG, "Nuwa SDK Service started.");
            isNuwaApiReady = true;
            runOnUiThread(() -> {
                buttonConnect.setText("連線");
                buttonConnect.setEnabled(true);
            });
        }
        @Override
        public void onWikiServiceStop() {
            Log.d(TAG, "Nuwa SDK Service stopped.");
            isNuwaApiReady = false;
        }
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

    private void connectToMqtt() {
        String brokerHost = editTextBroker.getText().toString().trim();
        String port = editTextPort.getText().toString().trim();
        final String topic = editTextTopic.getText().toString().trim();
        String clientId = editTextClientId.getText().toString().trim();

        if (brokerHost.isEmpty() || port.isEmpty() || topic.isEmpty()) {
            Toast.makeText(this, "Broker 地址、Port 和訂閱主題不能為空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (clientId.isEmpty()) {
            clientId = MqttClient.generateClientId();
        }

        String serverUri = brokerHost + ":" + port;
        if (!brokerHost.startsWith("tcp://") && !brokerHost.startsWith("wss://")) {
            serverUri = "tcp://" + serverUri;
        }

        speak("正在嘗試連線");
        textViewStatus.setText("狀態：正在連線...");
        buttonConnect.setEnabled(false);

        mqttClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);

        try {
            final String finalClientId = clientId;
            final String finalServerUri = serverUri;

            mqttClient.connect(connectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "MQTT Connection Success!");
                    speak("MQTT連線成功");
                    runOnUiThread(() -> textViewStatus.setText("狀態：連線成功"));

                    Intent intent = new Intent(MqttConnectActivity.this, MqttDashboardActivity.class);
                    intent.putExtra("SERVER_URI", finalServerUri);
                    intent.putExtra("CLIENT_ID", finalClientId);
                    intent.putExtra("TOPIC", topic);

                    startActivity(intent);

                    // *** 解決方案：將 finish() 註解掉或移除，以避免無限迴圈 ***
                    // finish();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    String errorMessage = "MQTT 連線失敗: " + exception.getMessage();
                    Log.e(TAG, errorMessage);
                    speak("MQTT連線失敗");
                    runOnUiThread(() -> {
                        textViewStatus.setText("狀態：" + errorMessage);
                        buttonConnect.setEnabled(true);
                    });
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            String errorMessage = "MQTT Exception: " + e.getMessage();
            speak("MQTT連線失敗");
            textViewStatus.setText("狀態：" + errorMessage);
            buttonConnect.setEnabled(true);
        }
    }

    private void speak(String text) {
        if (isEmulator()) {
            Toast.makeText(this, "TTS: " + text, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isNuwaApiReady && mRobotAPI != null) {
            mRobotAPI.startTTS(text);
        } else {
            Log.e(TAG, "Nuwa SDK is not ready, cannot speak.");
            Toast.makeText(this, "TTS 服務尚未準備完成", Toast.LENGTH_SHORT).show();
        }
    }

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
