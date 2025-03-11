package com.example.myapplication.ui;

import static java.lang.Double.parseDouble;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import android.view.View;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Mqtt {
    private static Mqtt instance;  // Singleton instance
    public MqttClient client;
    private static Config config;
    private final ExecutorService executorSend = Executors.newSingleThreadExecutor();
    private final ExecutorService executorReceive = Executors.newSingleThreadExecutor();

    // Private constructor to prevent instantiating directly
    private Mqtt() {}
    public static synchronized Mqtt getInstance() {
        if (instance == null) {
            instance = new Mqtt();
        }
        return instance;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
    public void connect(Context context) {
        executorReceive.execute(() -> {
        try {
            if (config == null) {
                config = ConfigManager.getConfig();
            }
            // Initialize the MqttClient only if it's not already initialized
            if (client == null) {
                // Generate a random number between 1 and 1,000,000
                Random random = new Random();
                int randomNumber = random.nextInt(1_000_000) + 1;

                System.out.println("Initializing MQTT Client...");

                // Use the random number to make the client ID unique
                String clientId = String.valueOf(randomNumber);

                // Initialize the MQTT client with the unique client ID
                client = new MqttClient("tcp://222.252.14.147:2883", clientId, new MemoryPersistence());
            }

            if (isConnected()) {
                System.out.println("Already connected to máy chủ .");
                runOnUiThread(context, "Đã kết nối đến máy chủ ");
                subscribe(config.getStaging());
                return; // No need to connect again
            }

            System.out.println("Connecting to MQTT Broker...");
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(false);
            connectOptions.setUserName("emservice");
            connectOptions.setPassword("Fj93m54j3g7h3iom47cb5y6Y".toCharArray());
            connectOptions.setAutomaticReconnect(true);
            connectOptions.setConnectionTimeout(10);  // 10 second timeout
            connectOptions.setKeepAliveInterval(300); // 30 seconds keep-alive

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("Connection lost: " + cause.getMessage());
                    // Handle reconnection in a separate thread
                    new Thread(() -> {
                        while (!isConnected()) {
                            try {
                                System.out.println("Attempting to reconnect...");
                                client.reconnect();
                                System.out.println("Reconnected to MQTT Broker.");
                            } catch (MqttException e) {
                                System.err.println("Reconnect failed: " + e.getMessage());
                                try {
                                    Thread.sleep(5000); // Wait before retrying
                                } catch (InterruptedException ex) {
                                    throw new RuntimeException(ex);
                                }
                            }
                        }
                    }).start();
                }


                private final Queue<String> messageBuffer = new ConcurrentLinkedQueue<>();
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Convert the message payload to a string

                        String payload = new String(message.getPayload());
                        System.out.println("Received message: " + payload);
                        // Thêm message vào buffer
                        messageBuffer.add(payload);

                        processNextMessage(); // Bắt đầu xử lý message trong buffer

                }

                private void processNextMessage() {
                        String payload = messageBuffer.poll(); // Lấy tin nhắn đầu tiên khỏi queue

                        // Extract key-value pairs
                        try {
                            // Kiểm tra nếu là tin nhắn SAVE (dùng dấu '|' để phân tách)

                            assert payload != null;
                            if (payload.startsWith("SAVE|")) {
                                if (config.getType().equals("Kiểm")) {
                                    return;
                                }
                                String[] parts = payload.split("\\|");

                                if (parts.length == 10) {
                                    String serial = parts[1].trim();
                                    Log.d("MQTT", "Serial: " + serial);

                                    // Kiểm tra và xử lý chuyển đổi double cho correction
                                    double correction = 0.0;
                                    try {
                                        correction = parseDouble(parts[2]);
                                        Log.d("MQTT", "Correction: " + correction);
                                    } catch (NumberFormatException e) {
                                        System.err.println("Invalid correction value: " + parts[2]);
                                    }

                                    // Kiểm tra kiểu dữ liệu và xử lý loại
                                    String type = parts[3].trim();
                                    Log.d("MQTT", "Type: " + type);

                                    // Kiểm tra và xử lý chuyển đổi double cho round
                                    double round = 0.0;
                                    try {
                                        round = parseDouble(parts[4]);
                                        Log.d("MQTT", "Round: " + round);
                                    } catch (NumberFormatException e) {
                                        System.err.println("Invalid round value: " + parts[4]);
                                    }

                                    // Kiểm tra và xử lý chuyển đổi double cho ratio
                                    double ratio = 0.0;
                                    try {
                                        ratio = parseDouble(parts[5]);
                                        Log.d("MQTT", "Ratio: " + ratio);
                                    } catch (NumberFormatException e) {
                                        System.err.println("Invalid ratio value: " + parts[5]);
                                    }

                                    // Kiểm tra và xử lý chuyển đổi double cho falseValue
                                    double falseValue = 0.0;
                                    try {
                                        falseValue = parseDouble(parts[6]);
                                        Log.d("MQTT", "False Value: " + falseValue);
                                    } catch (NumberFormatException e) {
                                        System.err.println("Invalid falseValue: " + parts[6]);
                                    }

                                    // Kiểm tra và xử lý chuyển đổi double cho ssDhmau


                                    // Kiểm tra giá trị tai và timestamp
                                    String tai = parts[7].trim();
                                    double ssDhmau = 0.0;
                                    try {
                                        ssDhmau = parseDouble(parts[8]);
                                        Log.d("MQTT", "SS Dhmau: " + ssDhmau);
                                    } catch (NumberFormatException e) {
                                        System.err.println("Invalid ssDhmau value: " + parts[7]);
                                    }
                                    String timestamp = parts[9].trim();

                                    Log.d("MQTT", "Tai: " + tai);
                                    Log.d("MQTT", "Timestamp: " + timestamp);

                                    // Insert vào cơ sở dữ liệu
                                    try (DatabaseHelper dbHelper = new DatabaseHelper(context)) {
                                        boolean isInserted = dbHelper.insertSaveMessage(serial, correction, type, round, ratio, tai, falseValue, ssDhmau, timestamp);
                                        if (isInserted) {
                                            Log.d("DatabaseHelper", "Data inserted successfully: " + serial);
                                        } else {
                                            Log.e("DatabaseHelper", "Failed to insert data: " + serial);
                                        }
                                    } catch (Exception e) {
                                        Log.e("DatabaseHelper", "Database error: " + e.getMessage(), e);
                                    }

                                    Log.d("MQTT", "Saved to DB: " + payload);
                                } else {
                                    System.err.println("Invalid SAVE format: " + payload);
                                }
                            } else {
                                // Xử lý các tin nhắn khác có dạng "KEY=VALUE"
                                String[] parts = payload.split("=");
                                if (parts.length == 2) {
                                    String key = parts[0].trim();
                                    String value = parts[1].trim();

                                    switch (key) {
                                        case "COMMAND":
                                            int command = Integer.parseInt(value);
                                            if (config.getType().equals("Kiểm") || command == 4) {  // Thêm điều kiện command == 4
                                                handleCommand(command);
                                            }
                                            break;

                                        case "ROUND":
                                            if (config.getType().equals("Kiểm")) {
                                                double roundValue = Double.parseDouble(value);
                                                Log.d("MQTT", "Received ROUND value: " + value);
                                                handleRound(roundValue);
                                            }
                                            break;

                                        case "ERROR":
                                            handleError(value);
                                            break;

                                        case "TAI":
                                            handleTai(value);
                                            break;

                                        default:
                                            System.err.println("Unknown message type: " + payload);
                                            break;
                                    }
                                } else {
                                    System.err.println("Invalid message format: " + payload);
                                }
                            }

                        } catch (Exception e) {

                            System.err.println("Error processing message: " + payload + " | " + e.getMessage());
                        }
                }

                private void handleCommand(int command) {
                    switch (command) {
                        case 1:
                            runOnUiThread(() -> Toast.makeText(context, "Bắt đầu", Toast.LENGTH_LONG).show());
                            config.setStart(true);
                            break;
                        case 2:
                            runOnUiThread(() -> Toast.makeText(context, "Kết thúc", Toast.LENGTH_LONG).show());
                            config.setStart(false);
                            break;
                        case 3:
                            runOnUiThread(() -> Toast.makeText(context, "Làm mới", Toast.LENGTH_LONG).show());
                            config.setRound(0);
                            config.setValueMau(0);
                            config.setFalseValueMeter(0);
                            config.setCorrection(0);
                            config.setRatio(0);
                            config.setPreviousAngle(0); // Lưu góc quay trước đó
                            config.setTotalRotation(0); // Tổng góc quay (tính cả phần thập phân)
                            config.setAngleDifference(-1);
                            config.setAngel(-1);
                            config.setAngelStart(-1);
                            break;
                        case 4:
                            runOnUiThread(() -> Toast.makeText(context, "Đang lưu", Toast.LENGTH_LONG).show());
                            handleSave();
                            break;
                        case 5:
                            runOnUiThread(() -> Toast.makeText(context, "Đang lưu", Toast.LENGTH_LONG).show());
                            handleSaveExcel();
                            break;
                        default:
                            System.out.println("Unknown command: " + command);
                            break;
                    }
                }

                private void handleRound(double roundValue) {
                    // Gán giá trị vào config
                    config.setValueMau(roundValue);
                }

                private void handleError(String errorValue) {
                    System.out.println("Received ERROR value: " + errorValue);
                    config.setSsDhm(errorValue);
                    runOnUiThread(() -> Toast.makeText(context, "Cập nhật tải và sai số", Toast.LENGTH_LONG).show());
                }

                private void handleTai(String taiValue) {
                    System.out.println("Received TAI value: " + taiValue);
                    config.setTai(taiValue);
                    runOnUiThread(() -> Toast.makeText(context, "Cập nhật tải và sai số", Toast.LENGTH_LONG).show());
                }
                private void handleSave() {
                    try {
                        System.out.println("Received SAVE value: ");

                    } catch (Exception e) {
                        System.err.println("Error in handleSave: " + e.getMessage());
                    }
                }
                private void handleSaveExcel() {
                    try {
                        System.out.println("Received SAVE value: ");

                        // Lấy thời gian hiện tại
                        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                        // Định dạng số với dấu phân cách thập phân là dấu chấm
                        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
                        DecimalFormat decimalFormat = new DecimalFormat("#.##", symbols);

                        // Kiểm tra nếu config không null
                        if (config == null) {
                            System.err.println("Config is null, cannot proceed with handleSave.");
                            return;
                        }
                        config.setRoundOld1(config.getRound());
                        config.setFalseValueMeterOld1(config.getFalseValueMeter());
                        config.setRatioOld1(config.getRatio());
                        config.setCorrectionOld1(config.getCorrection());

                        config.setRoundOld2(config.getRoundOld1());
                        config.setFalseValueMeterOld2(config.getFalseValueMeterOld1());
                        config.setRatioOld2(config.getRatioOld1());
                        config.setCorrectionOld2(config.getCorrectionOld1());
                        // Tạo payload
                        String payload = String.format("SAVE|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                                safeString(config.getSerial()),
                                safeDecimal(config.getCorrection(), decimalFormat),
                                safeString(config.getType()),
                                safeDecimal(config.getRound(), decimalFormat),
                                safeDecimal(config.getFalseValueMeter(), decimalFormat),
                                safeDecimal(config.getRatio(), decimalFormat),
                                safeString(config.getTai()),
                                safeString(config.getSsDhm()),
                                currentTime
                        );

                        // Gửi lệnh MQTT
                        Mqtt mqtt;
                        mqtt = Mqtt.getInstance();
                        sendMQTTCommand(mqtt,payload);
                    } catch (Exception e) {
                        System.err.println("Error in handleSave: " + e.getMessage());
                    }
                }

                // Hàm kiểm tra null cho String
                private String safeString(String value) {
                    return (value != null) ? value : "";
                }

                // Hàm kiểm tra null và định dạng số
                private String safeDecimal(Double value, DecimalFormat format) {
                    return (value != null) ? format.format(value) : "0";
                }


                private void runOnUiThread(Runnable action) {
                    // Check if we are on the main thread and run the action
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.getMainExecutor().execute(action);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("Message delivered successfully.");
                }
            });

            client.connect(connectOptions);  // Connect to the máy chủ 
            System.out.println("Connected to MQTT Broker.");

            runOnUiThread(context, "Kết nối thành công đến máy chủ ");

            // Optionally subscribe to topics after connection

            subscribe(config.getStaging()); // Example topic to subscribe to

        } catch (MqttException e) {
            System.err.println("Error during connection: " + e.getMessage());
            // Show Toast for connection failure
            Toast.makeText(context, "Kết nối thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        });
    }

    private String currentTopic = null; // Biến lưu topic hiện tại

    public void subscribe(String topic) {
        try {
            if (isConnected()) {
                if (currentTopic != null && !currentTopic.equals(topic)) {
                    // Hủy đăng ký topic cũ trước khi đăng ký mới
                    client.unsubscribe(currentTopic);
                    System.out.println("Unsubscribed from topic: " + currentTopic);
                }

                // Đăng ký vào topic mới
                client.subscribe(topic);
                System.out.println("Subscribed to topic: " + topic);

                // Cập nhật topic hiện tại
                currentTopic = topic;
            } else {
                System.err.println("Subscribe failed: Client is not connected.");
            }
        } catch (MqttException e) {
            System.err.println("Error during subscribe: " + e.getMessage());
        }
    }
    private void runOnUiThread(final Context context, final String message) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public void sendMQTTCommand(Mqtt mqtt, String payload) {
        executorSend.execute(() -> {
            if (mqtt.client != null && mqtt.client.isConnected()) {
                try {
                    MqttMessage message = new MqttMessage(payload.getBytes());
                    message.setQos(2); // Đặt QoS là 1 (hoặc 2 nếu cần)
                    mqtt.client.publish(config.getStaging(), message);
                    Log.d("MQTT", "Gửi thành công: " + payload);
                } catch (MqttException e) {
                    Log.e("MQTT", "Lỗi khi gửi lệnh: " + e.getMessage());
                }
            } else {
                Log.e("MQTT", "Client chưa kết nối, không thể gửi lệnh!");
            }
        });
    }
}
