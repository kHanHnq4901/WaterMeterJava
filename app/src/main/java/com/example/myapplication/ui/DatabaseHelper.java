package com.example.myapplication.ui;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.opencv.core.Core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "my_database.db";
    private static final int DATABASE_VERSION = 3; // Increase the version to trigger onUpgrade

    // Tên bảng & cột
    private static final String TABLE_SAVE = "save_messages";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_SERIAL = "serial";
    private static final String COLUMN_CORRECTION = "correction";
    private static final String COLUMN_TAI = "tai";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_ROUND = "round";
    private static final String COLUMN_RATIO = "ratio";
    private static final String COLUMN_FALSE_VALUE = "false_value";
    private static final String COLUMN_SS_DHMAU = "ss_dhmau";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + TABLE_SAVE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_SERIAL + " TEXT, "
                + COLUMN_CORRECTION + " REAL, "
                + COLUMN_TAI + " TEXT, "
                + COLUMN_TYPE + " TEXT, "
                + COLUMN_ROUND + " REAL, "
                + COLUMN_RATIO + " REAL, "
                + COLUMN_FALSE_VALUE + " REAL, "
                + COLUMN_SS_DHMAU + " REAL, "
                + COLUMN_TIMESTAMP + " TEXT)";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            String alterTableQuery = "ALTER TABLE " + TABLE_SAVE + " ADD COLUMN " + COLUMN_TYPE + " TEXT";
            db.execSQL(alterTableQuery);
        }
    }

    // Hàm chèn dữ liệu vào bảng save_messages
    public boolean insertSaveMessage(String serial, double correction, String type, double round, double ratio, String tai, double falseValue, double ssDhmau, String timestamp) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_SERIAL, serial);
            values.put(COLUMN_CORRECTION, correction);
            values.put(COLUMN_TYPE, type);
            values.put(COLUMN_ROUND, round);
            values.put(COLUMN_RATIO, ratio);
            values.put(COLUMN_TAI, tai);
            values.put(COLUMN_FALSE_VALUE, falseValue);
            values.put(COLUMN_SS_DHMAU, ssDhmau);
            values.put(COLUMN_TIMESTAMP, timestamp);

            long result = db.insert(TABLE_SAVE, null, values);

            if (result == -1) {
                Log.e("DatabaseHelper", "Insert failed for: " + serial);
                return false; // Lưu thất bại
            } else {
                Log.d("DatabaseHelper", "Insert successful, ID: " + result);
                return true; // Lưu thành công
            }
        } catch (Exception e) {
            Log.e("DatabaseHelper", "Insert error: ", e);
            return false;
        }
    }

    public List<SaveMessage> getAllSaveMessages() {
        List<SaveMessage> messages = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_SAVE;

        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery(query, null)) {

            while (cursor.moveToNext()) {
                @SuppressLint("Range") String serial = cursor.getString(cursor.getColumnIndex(COLUMN_SERIAL));
                @SuppressLint("Range") double correction = cursor.getDouble(cursor.getColumnIndex(COLUMN_CORRECTION));
                @SuppressLint("Range") String tai = cursor.getString(cursor.getColumnIndex(COLUMN_TAI));
                @SuppressLint("Range") String type = cursor.getString(cursor.getColumnIndex(COLUMN_TYPE));
                @SuppressLint("Range") double round = cursor.getDouble(cursor.getColumnIndex(COLUMN_ROUND));
                @SuppressLint("Range") double ratio = cursor.getDouble(cursor.getColumnIndex(COLUMN_RATIO));
                @SuppressLint("Range") double falseValue = cursor.getDouble(cursor.getColumnIndex(COLUMN_FALSE_VALUE));
                @SuppressLint("Range") double ssDhmau = cursor.getDouble(cursor.getColumnIndex(COLUMN_SS_DHMAU));
                @SuppressLint("Range") String timestamp = cursor.getString(cursor.getColumnIndex(COLUMN_TIMESTAMP));

                // Kiểm tra serial có phải là số hợp lệ không và chuyển đổi
                long serialNumber = 0;
                if (serial != null) {
                    try {
                        serialNumber = Long.parseLong(serial);  // Chuyển serial thành số (Long)
                    } catch (NumberFormatException e) {
                        Log.e("DatabaseHelper", "Serial không hợp lệ: " + serial, e);
                        // Xử lý trường hợp serial không hợp lệ tại đây
                    }
                }

                // Thêm đối tượng SaveMessage vào danh sách
                messages.add(new SaveMessage(serial, correction, tai, type, round, ratio, falseValue, ssDhmau, timestamp));
            }

            // Sắp xếp danh sách theo serial từ nhỏ đến lớn (sử dụng giá trị serialNumber đã chuyển đổi)
            Collections.sort(messages, new Comparator<SaveMessage>() {
                @Override
                public int compare(SaveMessage o1, SaveMessage o2) {
                    long serial1 = 0;
                    long serial2 = 0;
                    // Chuyển đổi serial thành long để so sánh
                    try {
                        serial1 = Long.parseLong(o1.getSerial());
                    } catch (NumberFormatException e) {
                        Log.e("DatabaseHelper", "Serial không hợp lệ: " + o1.getSerial(), e);
                    }
                    try {
                        serial2 = Long.parseLong(o2.getSerial());
                    } catch (NumberFormatException e) {
                        Log.e("DatabaseHelper", "Serial không hợp lệ: " + o2.getSerial(), e);
                    }
                    return Long.compare(serial1, serial2);  // So sánh serial từ nhỏ đến lớn
                }
            });

        } catch (Exception e) {
            Log.e("DatabaseHelper", "Query error: ", e);
        }

        return messages;
    }




    public void deleteAllSaveMessages() {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.execSQL("DELETE FROM " + TABLE_SAVE);
        } catch (Exception e) {
            Log.e("DatabaseHelper", "Delete error: ", e);
        }
    }
}
