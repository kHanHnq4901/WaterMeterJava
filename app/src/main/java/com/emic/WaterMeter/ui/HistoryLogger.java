package com.emic.watermeter.ui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryLogger {

    // Danh sách lịch sử dùng chung (tối đa 10 dòng)
    private static final List<String[]> historyList = new ArrayList<>();

    public static void logCurrentData(Config config) {
        // Lấy thời gian hiện tại
        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        // Lấy giá trị từ config
        String round = String.valueOf(config.getRound());
        String falseValue = String.valueOf(config.getFalseValueMeter());
        String correction = String.format("%.2f%%", config.getCorrection());

        // Thêm vào danh sách, tối đa 10 dòng
        if (historyList.size() >= 10) {
            historyList.remove(0);
        }

        historyList.add(new String[]{currentTime, round, falseValue, correction});
    }

    public static List<String[]> getHistoryList() {
        return historyList;
    }

    public static SpannableString getHistoryAsStyledString() {
        StringBuilder rawBuilder = new StringBuilder();

        // Dòng tiêu đề
        String header = String.format("%-10s %-10s %-15s %-10s\n", "Thời gian", "V đo", "Chênh lệch", "Sai số");
        rawBuilder.append(header);

        // Divider
        String divider = "-----------------------------------------------------------\n";
        rawBuilder.append(divider);

        // Dữ liệu từng dòng
        for (String[] record : historyList) {
            rawBuilder.append(String.format("%-10s %-10s %-15s %-10s\n", record[0], record[1], record[2], record[3]));
        }

        SpannableString spannable = new SpannableString(rawBuilder.toString());

        // Làm tiêu đề nhỏ lại (chữ đầu tiên đến hết dòng tiêu đề)
        int headerEnd = header.length();
        spannable.setSpan(new RelativeSizeSpan(0.85f), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }
}
