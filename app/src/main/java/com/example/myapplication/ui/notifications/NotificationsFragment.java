package com.example.myapplication.ui.notifications;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import android.net.Uri;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;


import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentNotificationsBinding;
import com.example.myapplication.ui.DatabaseHelper;
import com.example.myapplication.ui.SaveMessage;


import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private List<SaveMessage> messages;
    private DatabaseHelper dbHelper;
    private LinearLayout tableData;
    private int sortState = 0; // 0: Sắp xếp theo serial, 1: Sắp xếp theo timestamp
    private final ExecutorService executorRefreshTable = Executors.newSingleThreadExecutor();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {


        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        tableData = root.findViewById(R.id.tableData);  // Make sure the ID is correct in XML

        dbHelper = new DatabaseHelper(getContext());
        messages = dbHelper.getAllSaveMessages(); // Lấy dữ liệu từ cơ sở dữ liệu

            refreshTable();


        Button shareButton = root.findViewById(R.id.shareButton);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                    saveAndShareExcelFile();

            }
        });


        Button deleteButton = root.findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getContext())
                        .setMessage("Bạn có chắc chắn muốn xóa lịch sử?")
                        .setCancelable(false)
                        .setPositiveButton("Có", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dbHelper.deleteAllSaveMessages();
                                tableData.removeAllViews(); // Xóa tất cả các dòng trong bảng
                            }
                        })
                        .setNegativeButton("Không", null)
                        .show();
            }
        });

        Button sortButton = root.findViewById(R.id.sortButton);
        sortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortState = (sortState + 1) % 2;
                if (sortState == 0) {
                    // Sắp xếp theo serial, xử lý null và số không hợp lệ
                    messages.sort((m1, m2) -> {
                        int serial1 = parseSerial(m1.getSerial());
                        int serial2 = parseSerial(m2.getSerial());
                        return Integer.compare(serial1, serial2);
                    });
                } else {
                    // Sắp xếp theo timestamp, xử lý null
                    messages.sort((m1, m2) -> {
                        if (m1.getTimestamp() == null) return 1; // Nếu null, đẩy xuống cuối
                        if (m2.getTimestamp() == null) return -1;
                        return m1.getTimestamp().compareTo(m2.getTimestamp());
                    });
                }
                refreshTable(); // Cập nhật lại giao diện người dùng
            }
        });


        return root;
    }
    private int parseSerial(String serial) {
        if (serial == null || serial.trim().isEmpty()) return Integer.MAX_VALUE; // Nếu null hoặc rỗng, đẩy xuống cuối
        try {
            return Integer.parseInt(serial.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // Nếu không phải số hợp lệ, đẩy xuống cuối danh sách
        }
    }
    private void refreshTable() {
        executorRefreshTable.execute(() -> {
            // Đảm bảo rằng thao tác này chạy trên Main Thread
            requireActivity().runOnUiThread(() -> {
                tableData.removeAllViews(); // Xóa tất cả các dòng hiện tại trong bảng
                for (SaveMessage message : messages) {
                    addRowToTable(message, tableData);
                }
            });
        });
    }


    private void addRowToTable(SaveMessage message, LinearLayout tableData) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        double correction = message.getCorrection();
        if (correction < -1 || correction > 1) {
            row.setBackgroundColor(getResources().getColor(R.color.red)); // Màu đỏ nếu correction < -1 hoặc > 1
        } else {
            row.setBackgroundColor(getResources().getColor(R.color.green)); // Màu xanh nếu correction trong khoảng -1 và 1
        }

        TextView serialView = createTextView(message.getSerial());
        TextView correctionView = createTextView(String.valueOf(message.getCorrection()));
        TextView taiView = createTextView(message.getTai());
        TextView typeView = createTextView(String.valueOf(message.getType()));
        TextView roundView = createTextView(String.valueOf(message.getRound()));
        TextView ratioView = createTextView(String.valueOf(message.getRatio()));
        TextView falseValueView = createTextView(String.valueOf(message.getFalseValue()));
        TextView ssDhmauView = createTextView(String.valueOf(message.getSsDhmau()));
        TextView timestampView = createTextView(message.getTimestamp());

        row.addView(serialView);
        row.addView(createDivider());
        row.addView(correctionView);
        row.addView(createDivider());
        row.addView(taiView);
        row.addView(createDivider());
        row.addView(typeView);
        row.addView(createDivider());
        row.addView(roundView);
        row.addView(createDivider());
        row.addView(ratioView);
        row.addView(createDivider());
        row.addView(falseValueView);
        row.addView(createDivider());
        row.addView(ssDhmauView);
        row.addView(createDivider());
        row.addView(timestampView);

        tableData.addView(row);
        tableData.addView(createRowDivider());
    }

    private View createDivider() {
        View divider = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT);
        params.setMargins(5, 0, 5, 0);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getResources().getColor(R.color.black)); // Màu của đường kẻ
        return divider;
    }

    private View createRowDivider() {
        View divider = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getResources().getColor(R.color.black)); // Màu của đường kẻ
        return divider;
    }

    private TextView createTextView(String text) {
        TextView textView = new TextView(getContext());
        int widthInPx = convertDpToPx();
        textView.setLayoutParams(new LinearLayout.LayoutParams(widthInPx, LinearLayout.LayoutParams.WRAP_CONTENT));
        textView.setGravity(Gravity.CENTER);
        textView.setText(text);
        textView.setTextSize(12);
        return textView;
    }

    private int convertDpToPx() {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return (int) (95 * density);
    }

    private void saveAndShareExcelFile() {
        try {
            File directory = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Reports");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String fileName = "Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".xlsx";
            File file = new File(directory, fileName);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Data");

            // Tạo kiểu tiêu đề
            CellStyle headerStyle = getHeaderCellStyle(workbook);

            // Tạo kiểu cho hàng màu đỏ
            CellStyle redStyle = workbook.createCellStyle();
            redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Tạo kiểu cho hàng màu xanh
            CellStyle greenStyle = workbook.createCellStyle();
            greenStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            greenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Tạo tiêu đề cột
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Serial", "Sai số", "Tải", "Loại", "Lượng nước", "Tỉ lệ", "Sai lệch", "Sai số dhMau", "Thời gian"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Ghi dữ liệu và áp dụng màu
            int rowNum = 1;
            for (SaveMessage message : messages) {
                Row row = sheet.createRow(rowNum++);
                CellStyle rowStyle = (message.getCorrection() < -1 || message.getCorrection() > 1) ? redStyle : greenStyle;

                Cell cell0 = row.createCell(0);
                cell0.setCellValue(message.getSerial());
                cell0.setCellStyle(rowStyle);

                Cell cell1 = row.createCell(1);
                cell1.setCellValue(message.getCorrection());
                cell1.setCellStyle(rowStyle);

                Cell cell2 = row.createCell(2);
                cell2.setCellValue(message.getTai());
                cell2.setCellStyle(rowStyle);

                Cell cell3 = row.createCell(3);
                cell3.setCellValue(message.getType());
                cell3.setCellStyle(rowStyle);

                Cell cell4 = row.createCell(4);
                cell4.setCellValue(message.getRound());
                cell4.setCellStyle(rowStyle);

                Cell cell5 = row.createCell(5);
                cell5.setCellValue(message.getRatio());
                cell5.setCellStyle(rowStyle);

                Cell cell6 = row.createCell(6);
                cell6.setCellValue(message.getFalseValue());
                cell6.setCellStyle(rowStyle);

                Cell cell7 = row.createCell(7);
                cell7.setCellValue(message.getSsDhmau());
                cell7.setCellStyle(rowStyle);

                Cell cell8 = row.createCell(8);
                cell8.setCellValue(message.getTimestamp());
                cell8.setCellStyle(rowStyle);
            }

            // Ghi vào file
            FileOutputStream fileOut = new FileOutputStream(file);
            workbook.write(fileOut);
            fileOut.close();
            workbook.close();

            // Chia sẻ file
            shareFile(file);
        } catch (IOException e) {
            Log.e("ExcelExport", "Lỗi khi lưu file", e);
            Toast.makeText(getContext(), "Lưu file thất bại!", Toast.LENGTH_SHORT).show();
        }
    }

    private CellStyle getHeaderCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }


    private void shareFile(File file) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // Định dạng file Excel
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Cấp quyền đọc
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ file"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
