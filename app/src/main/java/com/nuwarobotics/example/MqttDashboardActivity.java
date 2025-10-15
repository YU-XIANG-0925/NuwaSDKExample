package com.nuwarobotics.example;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nuwarobotics.service.IClientId;
import com.nuwarobotics.service.agent.NuwaRobotAPI;
import com.nuwarobotics.service.agent.RobotEventListener;
import com.nuwarobotics.service.facecontrol.IonCompleteListener;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MqttDashboardActivity extends AppCompatActivity implements MqttManager.MqttMessageListener {

    private final String TAG = "MqttDashboardActivity";

    private TextView textViewLog;
    private Map<String, TextView> motorTextViews = new HashMap<>();
    private ListView listViewMotions;

    private NuwaRobotAPI mRobotAPI;
    private IClientId mIClientId;
    private boolean isNuwaApiReady = false;

    private Gson gson = new Gson();
    private Handler mqttPublishHandler = new Handler();
    private List<String> motionList = new ArrayList<>();

    private static class MotorCommand {
        String motorId;
        float angle;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqtt_dashboard);

        if (!MqttManager.getInstance().isConnected()) {
            Toast.makeText(this, "MQTT is not connected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        initNuwaSdk();

        listViewMotions.setOnItemClickListener((parent, view, position, id) -> {
            if (isNuwaApiReady && mRobotAPI != null) {
                String motionName = motionList.get(position);
                logMessage("Playing motion: " + motionName);
                try {
                    mRobotAPI.playMotion(motionName, new IonCompleteListener.Stub() {
                        @Override
                        public void onComplete(String process_name) throws RemoteException {
                            runOnUiThread(() -> {
                                logMessage("Motion completed: " + process_name);
                            });
                        }
                    });
                } catch (Exception e) {
                    logMessage("Error playing motion: " + e.getMessage());
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        MqttManager.getInstance().setListener(this);
        startPublishingMotorAngles();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MqttManager.getInstance().removeListener();
        stopPublishingMotorAngles();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRobotAPI != null) {
            mRobotAPI.release();
        }
    }

    private void initNuwaSdk() {
        mIClientId = new IClientId(this.getPackageName());
        mRobotAPI = new NuwaRobotAPI(this, mIClientId);

        if (isEmulator()) {
            isNuwaApiReady = true;
            onNuwaSdkReady();
        } else {
            mRobotAPI.registerRobotEventListener(robotEventListener);
        }
    }

    private void onNuwaSdkReady() {
        // Get motion list and set adapter
        motionList = mRobotAPI.getMotionList();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, motionList);
        listViewMotions.setAdapter(adapter);

        // Start publishing motor angles
        startPublishingMotorAngles();
    }

    private void startPublishingMotorAngles() {
        mqttPublishHandler.postDelayed(publishRunnable, 1000);
    }

    private void stopPublishingMotorAngles() {
        mqttPublishHandler.removeCallbacks(publishRunnable);
    }

    private Runnable publishRunnable = new Runnable() {
        @Override
        public void run() {
            if (isNuwaApiReady && mRobotAPI != null) {
                // This is an assumed method. Replace with the actual method if different.
                // Map<String, Float> motorAngles = mRobotAPI.getMotorAngles();
                // As a placeholder, I will create some dummy data.
                Map<String, Float> motorAngles = new HashMap<>();
                motorAngles.put("NECK_Y", 10.0f);
                motorAngles.put("NECK_Z", 20.0f);

                String jsonPayload = gson.toJson(motorAngles);
                MqttManager.getInstance().publish(jsonPayload);
                logMessage("Published motor angles: " + jsonPayload);
            }
            mqttPublishHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onMessageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        runOnUiThread(() -> {
            logMessage("收到訊息 (" + topic + "): " + payload);
            handleMotorCommand(payload);
        });
    }

    private void handleMotorCommand(String jsonPayload) {
        try {
            Type listType = new TypeToken<List<MotorCommand>>() {}.getType();
            List<MotorCommand> commands = gson.fromJson(jsonPayload, listType);

            if (commands == null || commands.isEmpty()) {
                logMessage("收到的指令為空或格式不符。");
                return;
            }

            for (MotorCommand command : commands) {
                updateMotorAngle(command.motorId, command.angle);

                if (!isEmulator() && isNuwaApiReady && mRobotAPI != null) {
                    int motorId = getMotorIdFromString(command.motorId);
                    if (motorId != -1) {
                        logMessage("控制馬達: " + command.motorId + " -> " + command.angle);
                        mRobotAPI.ctlMotor(motorId, command.angle, 45);
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
        if (motorName.equals("NECK_Y")) {
            return NuwaRobotAPI.MOTOR_NECK_Y;
        } else if (motorName.equals("NECK_Z")) {
            return NuwaRobotAPI.MOTOR_NECK_Z;
        } else if (motorName.equals("RIGHT_SHOULDER_Z")) {
            return NuwaRobotAPI.MOTOR_RIGHT_SHOULDER_Z;
        } else if (motorName.equals("RIGHT_SHOULDER_Y")) {
            return NuwaRobotAPI.MOTOR_RIGHT_SHOULDER_Y;
        } else if (motorName.equals("RIGHT_SHOULDER_X")) {
            return NuwaRobotAPI.MOTOR_RIGHT_SHOULDER_X;
        } else if (motorName.equals("RIGHT_ELBOW_Y")) {
            return NuwaRobotAPI.MOTOR_RIGHT_ELBOW_Y;
        } else if (motorName.equals("LEFT_SHOULDER_Z")) {
            return NuwaRobotAPI.MOTOR_LEFT_SHOULDER_Z;
        } else if (motorName.equals("LEFT_SHOULDER_Y")) {
            return NuwaRobotAPI.MOTOR_LEFT_SHOULDER_Y;
        } else if (motorName.equals("LEFT_SHOULDER_X")) {
            return NuwaRobotAPI.MOTOR_LEFT_SHOULDER_X;
        } else if (motorName.equals("LEFT_ELBOW_Y")) {
            return NuwaRobotAPI.MOTOR_LEFT_ELBOW_Y;
        } else {
            return -1;
        }
    }

    private void bindViews() {
        textViewLog = findViewById(R.id.textView_log);
        textViewLog.setText("");
        listViewMotions = findViewById(R.id.listView_motions);

        motorTextViews.put("NECK_Y", (TextView) findViewById(R.id.textView_neck_y));
        motorTextViews.put("NECK_Z", (TextView) findViewById(R.id.textView_neck_z));
        motorTextViews.put("RIGHT_SHOULDE_Z", (TextView) findViewById(R.id.textView_r_shoulder_z));
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
            runOnUiThread(() -> onNuwaSdkReady());
        }
        @Override
        public void onWikiServiceStop() { isNuwaApiReady = false; }
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
