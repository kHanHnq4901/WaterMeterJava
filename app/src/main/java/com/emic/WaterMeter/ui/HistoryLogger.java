package com.emic.watermeter.ui;

import android.annotation.SuppressLint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class HistoryLogger {

    // Danh sách lịch sử dùng chung (tối đa 10 dòng)
    private static final List<double[]> historyList = new ArrayList<>();

    public static void logCurrentData(Config config) {
        // Lấy giá trị từ config và làm tròn 2 chữ số sau dấu phẩy
        double round = Math.round(config.getRound() * 100.0) / 100.0;
        double falseValue = Math.round(config.getFalseValueMeter() * 100.0) / 100.0;
        double correction = Math.round(config.getCorrection() * 100.0) / 100.0;

        // Thêm vào danh sách, tối đa 10 dòng
        if (historyList.size() >= 10) {
            historyList.remove(0);
        }

        historyList.add(new double[]{round, falseValue, correction});
    }

    public static List<double[]> getHistoryList() {
        return historyList;
    }

    @SuppressLint("DefaultLocale")
    public static SpannableString getHistoryAsStyledString() {
        StringBuilder rawBuilder = new StringBuilder();

        // Dòng tiêu đề
        String header = String.format("%-10s %-15s %-10s\n", "V đo", "Chênh lệch", "Sai số");
        rawBuilder.append(header);

        // Divider
        String divider = "-----------------------------------------------------------\n";
        rawBuilder.append(divider);

        // Dữ liệu từng dòng
        for (double[] record : historyList) {
            if (record.length >= 3) {
                rawBuilder.append(String.format(
                        "%-10.2f %-15.2f %-10s\n",
                        record[0],
                        record[1],
                        record[2] + "%" // chỉ thêm %
                ));
            }
        }

        SpannableString spannable = new SpannableString(rawBuilder.toString());

        // Làm tiêu đề nhỏ lại
        int headerEnd = header.length();
        spannable.setSpan(new RelativeSizeSpan(0.85f), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

}
