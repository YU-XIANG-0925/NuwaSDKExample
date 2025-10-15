package com.nuwarobotics.example;

import android.content.Context;
import android.util.Log;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttManager {

    private static final String TAG = "MqttManager";
    private static MqttManager instance;
    private MqttAndroidClient mqttClient;
    private String topic;

    private MqttMessageListener listener;

    public interface MqttMessageListener {
        void onMessageArrived(String topic, MqttMessage message);
    }

    private MqttManager() {
        // Private constructor for singleton
    }

    public static synchronized MqttManager getInstance() {
        if (instance == null) {
            instance = new MqttManager();
        }
        return instance;
    }

    public void setListener(MqttMessageListener listener) {
        this.listener = listener;
    }

    public void removeListener() {
        this.listener = null;
    }

    public void connect(Context context, String serverUri, String clientId, String topic, IMqttActionListener listener) {
        this.topic = topic;
        mqttClient = new MqttAndroidClient(context.getApplicationContext(), serverUri, clientId);
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, "Connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "Message arrived: " + new String(message.getPayload()));
                if (MqttManager.this.listener != null) {
                    MqttManager.this.listener.onMessageArrived(topic, message);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "Delivery complete");
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        try {
            mqttClient.connect(options, null, listener);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe() {
        if (mqttClient != null && mqttClient.isConnected() && topic != null) {
            try {
                mqttClient.subscribe(topic, 0, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Subscribed to " + topic);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.d(TAG, "Failed to subscribe to " + topic);
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    public void publish(String payload) {
        if (mqttClient != null && mqttClient.isConnected() && topic != null) {
            try {
                MqttMessage message = new MqttMessage(payload.getBytes());
                mqttClient.publish(topic, message);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}
