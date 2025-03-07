package com.example.myapplication.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ConfigManager {
    private static ConfigManager instance;
    private static Config config;
    private static SharedPreferences sharedPreferences;

    // Constructor private để tránh khởi tạo trực tiếp
    private ConfigManager(Context context) {
        sharedPreferences = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        config = new Config();
        loadConfigFromPreferences();
    }

    // Khởi tạo singleton
    public static synchronized void initialize(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
    }

    // Lấy đối tượng config nếu nó đã được khởi tạo, nếu không thì tạo mới
    public static Config getConfig() {
        if (config == null) {
            config = new Config();
            loadConfigFromPreferences();
        }
        return config;
    }

    // Phương thức lấy instance của ConfigManager
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager chưa được khởi tạo! Hãy gọi initialize() trước.");
        }
        return instance;
    }

    // Phương thức load cấu hình từ SharedPreferences
    public static void loadConfigFromPreferences() {
        if (sharedPreferences != null) {

            config.setSerial(sharedPreferences.getString("serial", ""));
            config.setStaging(sharedPreferences.getString("staging", "1"));
            config.setErrQI(sharedPreferences.getString("errQI", "0"));
            config.setErrQII(sharedPreferences.getString("errQII", "0"));
            config.setErrQIII(sharedPreferences.getString("errQIII", "0"));
            config.setErrQ3(sharedPreferences.getString("errQ3", "0"));
            config.setType(sharedPreferences.getString("type", "Kiểm"));
            config.setConnect(false);
            config.setStart(false);

            // Log các giá trị
            Log.d("ConfigManager", "Serial: " + config.getSerial());
            Log.d("ConfigManager", "Staging: " + config.getStaging());
            Log.d("ConfigManager", "ErrQI: " + config.getErrQI());
            Log.d("ConfigManager", "ErrQII: " + config.getErrQII());
            Log.d("ConfigManager", "ErrQIII: " + config.getErrQIII());
            Log.d("ConfigManager", "ErrQ3: " + config.getErrQ3());
            Log.d("ConfigManager", "Type: " + config.getType());
            Log.d("ConfigManager", "Connect: " + config.getIsConnect());
            Log.d("ConfigManager", "Start: " + config.getIsStart());
        }
    }

    // Lưu cấu hình vào SharedPreferences
    public void saveConfigToPreferences() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("serial", config.getSerial());
            editor.putString("staging", config.getStaging());
            editor.putString("errQI", config.getErrQI());
            editor.putString("errQII", config.getErrQII());
            editor.putString("errQIII", config.getErrQIII());
            editor.putString("errQ3", config.getErrQ3());
            editor.putString("type", config.getType());
            editor.putString("saturation", String.valueOf(config.getSaturation()));
            editor.apply(); // Hoặc editor.commit();
        }
    }
}
