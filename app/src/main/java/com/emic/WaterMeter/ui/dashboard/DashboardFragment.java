package com.emic.watermeter.ui.dashboard;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;

import com.emic.watermeter.R;
import com.emic.watermeter.databinding.FragmentDashboardBinding;

import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.emic.watermeter.ui.Config;
import com.emic.watermeter.ui.ConfigManager;
import com.emic.watermeter.ui.HistoryLogger;
import com.emic.watermeter.ui.Mqtt;
import com.emic.watermeter.ui.home.HomeFragment;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class DashboardFragment extends HomeFragment {
    private FragmentDashboardBinding binding;

    protected com.emic.watermeter.ui.Mqtt mqtt;
    private static Config config;
    private SharedPreferences sharedPreferences;
    private final List<String[]> historyList = new ArrayList<>();
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        config = ConfigManager.getConfig();
        mqtt = Mqtt.getInstance();
        setupRadioButtons();
        setupSeekBar();
        setupUIVisibility();
        // Button Setup
        // Nút "Làm Mới"
        binding.buttonLamMoi.setOnClickListener(v -> {
            resetValues();
            mqtt.sendMQTTCommand(mqtt, "COMMAND=3", requireActivity());

            // Ghi log lịch sử từ config
            HistoryLogger.logCurrentData(config);
        });

        binding.buttonLuu.setOnClickListener(v -> {
            saveCurrentConfig();
            mqtt.sendMQTTCommand(mqtt, "COMMAND=4", requireActivity());
        });

        binding.buttonLuuExcel.setOnClickListener(v -> {
            saveCurrentConfig();
            mqtt.sendMQTTCommand(mqtt, "COMMAND=5", requireActivity());
        });


        Button buttonBatDau = binding.buttonBatDau;
        Button buttonKetThuc = binding.buttonKetThuc;
        updateStartStopButtonVisibility(buttonBatDau, buttonKetThuc);

        // Nút "Bắt Đầu"
        binding.buttonBatDau.setOnClickListener(v -> {
            toggleStartButton(binding.buttonBatDau, binding.buttonKetThuc);
        });

        // Nút "Kết Thúc"
        binding.buttonKetThuc.setOnClickListener(v -> {
            toggleEndButton(binding.buttonBatDau, binding.buttonKetThuc);
        });
        binding.iconHistory.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle("Lịch sử làm mới");

            SpannableString historyText = HistoryLogger.getHistoryAsStyledString(); // Lấy lịch sử

            builder.setMessage(historyText);
            builder.setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss());
            builder.show();
        });

        startCamera();
        return root;
    }

    private void setupRadioButtons() {
        RadioButton[] radioButtons = {
                binding.radioButtonQI, binding.radioButtonQII,
                binding.radioButtonQIII, binding.radioButtonQ3
        };
        String[] taiOptions = {"QI", "QII", "QIII", "Q3"};

        // Set initial state
        for (int i = 0; i < radioButtons.length; i++) {
            if (taiOptions[i].equals(config.getTai())) {
                radioButtons[i].setChecked(true);
                break;
            }
        }

        for (int i = 0; i < radioButtons.length; i++) {
            final String tai = taiOptions[i];

            // Click chọn
            radioButtons[i].setOnClickListener(v -> {
                String value = getErrValue(tai);
                double errValue = value != null && !value.isEmpty() ? Double.parseDouble(value) : 0.0;
                onRadioButtonClicked(tai, errValue);
            });

            // Long press để hiện ô nhập
            radioButtons[i].setOnLongClickListener(v -> {
                showEditValueDialog(tai);
                return true;
            });
        }
    }

    private void showEditValueDialog(String tai) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER);

        // Đặt giá trị hiện tại vào EditText (nếu có)
        String currentValue = getErrValue(tai);
        if (currentValue != null && !currentValue.isEmpty()) {
            input.setText(currentValue);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Cập nhật V chuẩn cho " + tai)
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String value = input.getText().toString().trim();

                    // Lưu giá trị theo từng loại `tai`
                    switch (tai) {
                        case "QI":
                            config.setErrQI(value);
                            break;
                        case "QII":
                            config.setErrQII(value);
                            break;
                        case "QIII":
                            config.setErrQIII(value);
                            break;
                        case "Q3":
                            config.setErrQ3(value);
                            break;
                    }

                    // Xử lý sau khi lưu (cập nhật giao diện hoặc lưu file/db)
                    onErrValueSaved(tai, value);

                    Toast.makeText(requireContext(), "Đã lưu giá trị: " + value + " cho " + tai, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
    private void onErrValueSaved(String tai, String value) {
        // Lưu giá trị mới vào SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        switch (tai) {
            case "QI":
                editor.putString("errQI", value);
                config.setErrQI(value);
                break;
            case "QII":
                editor.putString("errQII", value);
                config.setErrQII(value);
                break;
            case "QIII":
                editor.putString("errQIII", value);
                config.setErrQIII(value);
                break;
            case "Q3":
                editor.putString("errQ3", value);
                config.setErrQ3(value);
                break;
        }
        editor.apply(); // hoặc editor.commit();

        // Hiển thị thông báo
        Toast.makeText(requireContext(), "Đã cập nhật thành công giá trị cho " + tai, Toast.LENGTH_SHORT).show();
    }




    private void setupSeekBar() {
        SeekBar seekBar = binding.seekBarSaturation;
        sharedPreferences = requireContext().getSharedPreferences("AppConfig", Context.MODE_PRIVATE);

        // Thiết lập giá trị ban đầu cho SeekBar và TextView
        int initialSaturation = Integer.parseInt(sharedPreferences.getString("saturation", "50"));
        seekBar.setProgress(initialSaturation);
        TextView textView = binding.textViewSaturationValue;
        textView.setText(String.valueOf(initialSaturation));
        lowRed = new Scalar(0, initialSaturation, 1);
        lowRed1 = new Scalar(150, initialSaturation, 1);
        Log.d("saturation", String.valueOf(initialSaturation));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                config.setSaturation(progress);
                lowRed = new Scalar(0, progress, 1);
                lowRed1 = new Scalar(150, progress, 1);
                textView.setText(String.valueOf(progress));

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("saturation", String.valueOf(progress));
                editor.apply();  // Lưu thay đổi
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupUIVisibility() {
        if ("Kiểm".equals(config.getType())) {
            binding.radioGroup.setVisibility(View.GONE);
            binding.linearLayout.setVisibility(View.GONE);
        }
    }

    private void saveCurrentConfig() {
        // Bước 1: Di chuyển Old1 → Old2
        config.setRoundOld2(config.getRoundOld1());
        config.setFalseValueMeterOld2(config.getFalseValueMeterOld1());
        config.setRatioOld2(config.getRatioOld1());
        config.setCorrectionOld2(config.getCorrectionOld1());

        // Bước 2: Di chuyển Old → Old1
        config.setRoundOld1(config.getRoundOld());
        config.setFalseValueMeterOld1(config.getFalseValueMeterOld());
        config.setRatioOld1(config.getRatioOld());
        config.setCorrectionOld1(config.getCorrectionOld());

        // Bước 3: Cập nhật giá trị hiện tại → Old
        config.setRoundOld(config.getRound());
        config.setFalseValueMeterOld(config.getFalseValueMeter());
        config.setRatioOld(config.getRatio());
        config.setCorrectionOld(config.getCorrection());
    }


    private String getErrValue(String tai) {
        switch (tai) {
            case "QI": return config.getErrQI();
            case "QII": return config.getErrQII();
            case "QIII": return config.getErrQIII();
            case "Q3": return config.getErrQ3();
            default: return "0";
        }
    }

    private void onRadioButtonClicked(String tai, double errValue) {
        config.setTai(tai);
        config.setSsDhm(errValue);
        mqtt.sendMQTTCommand(mqtt,"ERROR=" + errValue,requireActivity());
        mqtt.sendMQTTCommand(mqtt,"TAI=" + tai,requireActivity());
        Log.d("RadioGroup", tai + " selected");
    }



    private void updateStartStopButtonVisibility(Button buttonBatDau, Button buttonKetThuc) {
        if (config.getIsStart()) {
            buttonBatDau.setVisibility(View.GONE);
            buttonKetThuc.setVisibility(View.VISIBLE);
        } else {
            buttonBatDau.setVisibility(View.VISIBLE);
            buttonKetThuc.setVisibility(View.GONE);
        }
    }



    private void resetValues() {
        // Sử dụng setter để cập nhật giá trị cho previousAngle và totalRotation
        config.setPreviousAngle(0);
        config.setTotalRotation(0);

        // Cập nhật các biến còn lại trực tiếp
        config.setAngleDifference(-1);
        config.setAngel(-1);
        config.setAngelStart(-1);

        // Đặt lại số vòng (round) bằng setter
        config.setRound(0);
    }


    /**
     * Xử lý sự kiện bấm nút Bắt đầu / Kết thúc
     */
    private Timer timer;
    private boolean isRunning = false;

    private void toggleStartButton(Button buttonBatDau, Button buttonKetThuc) {
        Log.d("ToggleStartButton", "Bắt đầu vòng lặp");

        if (isRunning) {
            Log.d("ToggleStartButton", "Đã chạy, không khởi tạo lại.");
            return;
        }
        // Gửi lệnh bắt đầu
        mqtt.sendMQTTCommand(mqtt, "COMMAND=1",requireActivity());
        config.setStart(true);
        isRunning = true;
        // Cập nhật UI
        buttonBatDau.setVisibility(View.GONE);
        buttonKetThuc.setVisibility(View.VISIBLE);
    }

    private void toggleEndButton(Button buttonBatDau, Button buttonKetThuc) {
        Log.d("ToggleEndButton", "Đang dừng vòng lặp");

        isRunning = false;

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        // Cập nhật UI
        buttonKetThuc.setVisibility(View.GONE);
        buttonBatDau.setVisibility(View.VISIBLE);

        // Gửi lệnh dừng
        mqtt.sendMQTTCommand(mqtt, "COMMAND=2",requireActivity());
        config.setStart(false);
    }

    private static final double THRESHOLD = 1; // Ngưỡng thay đổi nhỏ
    private final Queue<Double> recentRoundValues = new LinkedList<>();
    private static final int MAX_SIZE = 10; // Kích thước của hàng đợi
    private final Queue<Long> timestamps = new LinkedList<>();
    Mat hierarchy = new Mat();
    Mat dst = new Mat();
    Mat mask1 = new Mat();
    Mat mask2 = new Mat();
    Scalar lowRed = new Scalar(0,50,100);
    Scalar highRed = new Scalar(10, 255, 255);
    Scalar lowRed1 = new Scalar(150, 50, 100);
    Scalar highRed1 = new Scalar(180, 255, 255);

    @OptIn(markerClass = ExperimentalGetImage.class)
    private CameraControl cameraControl; // Thêm biến CameraControl

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Tạo ImageAnalysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), imageProxy -> {
                    try {
                        updateUIWithMQTT();
                        Mat matRGB = imageProxyToMat(imageProxy);
                        Mat matHSV = new Mat();

                        Imgproc.cvtColor(matRGB, matHSV, Imgproc.COLOR_RGB2HSV);
                        Point center = new Point((double) matHSV.width() / 2, (double) matHSV.height() / 2);

                        Core.inRange(matHSV, lowRed, highRed, mask1);
                        Core.inRange(matHSV, lowRed1, highRed1, mask2);
                        Core.bitwise_or(mask1, mask2, dst);

                        List<MatOfPoint> contours = new ArrayList<>();
                        Imgproc.findContours(dst, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

                        MatOfPoint bestContour = null;
                        double maxArea = 0;
                        double minDistance = Double.MAX_VALUE;
                        for (MatOfPoint c : contours) {
                            double contourArea = Imgproc.contourArea(c);

                            if (contourArea > 1000 && contourArea < 25000) {
                                // Tính convex hull
                                MatOfInt hull = new MatOfInt();
                                Imgproc.convexHull(c, hull);
                                List<Point> hullPoints = new ArrayList<>();
                                for (int i = 0; i < hull.rows(); i++) {
                                    int index = (int) hull.get(i, 0)[0];
                                    hullPoints.add(c.toList().get(index));
                                }

                                // Tính centroid
                                Point centroid = new Point();
                                for (Point pt : hullPoints) {
                                    centroid.x += pt.x;
                                    centroid.y += pt.y;
                                }
                                centroid.x /= hullPoints.size();
                                centroid.y /= hullPoints.size();

                                // Tính khoảng cách đến center
                                double distance = distanceCalculate(center, centroid);

                                // Chọn contour gần nhất
                                if (distance < 100 && distance < minDistance) {
                                    minDistance = distance;
                                    bestContour = c;
                                }
                            }
                        }

                        // Nếu tìm được contour tốt nhất, xử lý nó
                        if (bestContour != null) {
                            MatOfInt hull = new MatOfInt();
                            Imgproc.convexHull(bestContour, hull);
                            List<Point> hullPoints = new ArrayList<>();
                            for (int i = 0; i < hull.rows(); i++) {
                                int index = (int) hull.get(i, 0)[0];
                                hullPoints.add(bestContour.toList().get(index));
                            }

                            // Tính lại centroid cho contour tốt nhất
                            Point centroid = new Point();
                            for (Point pt : hullPoints) {
                                centroid.x += pt.x;
                                centroid.y += pt.y;
                            }
                            centroid.x /= hullPoints.size();
                            centroid.y /= hullPoints.size();

                            Point farthestPoint = null;
                            double maxDistance = 0;

                            for (Point pt : hullPoints) {
                                double distance = distanceCalculate(centroid, pt);
                                if (distance > maxDistance) {
                                    maxDistance = distance;
                                    farthestPoint = pt;
                                }
                            }

                            // Vẽ contour tốt nhất
                            for (int i = 0; i < hullPoints.size(); i++) {
                                Point pt1 = hullPoints.get(i);
                                Point pt2 = hullPoints.get((i + 1) % hullPoints.size());
                                Imgproc.line(matRGB, pt1, pt2, new Scalar(0, 255, 0), 2);
                            }

                            if (farthestPoint != null) {
                                Imgproc.circle(matRGB, farthestPoint, 5, new Scalar(255, 0, 0), -1);
                                config.setAngel(Math.toDegrees(Math.atan2(farthestPoint.y - centroid.y, farthestPoint.x - centroid.x)));

                                if (config.getAngel() < 0) {
                                    config.setAngel(config.getAngel() + 360);
                                }

                                if (config.getAngelStart() < 0) {
                                    config.setAngelStart(config.getAngel());
                                }

                                if (config.getPreviousAngle() != 0) {
                                    long currentTimeMillis = System.currentTimeMillis();
                                    updateRotationCount(config.getAngel(), currentTimeMillis);
                                }

                                config.setPreviousAngle(config.getAngel());
                            }
                        }


                        Imgproc.line(matRGB, new Point(center.x - 10, center.y), new Point(center.x + 10, center.y), new Scalar(0, 0, 255), 2);
                        Imgproc.line(matRGB, new Point(center.x, center.y - 10), new Point(center.x, center.y + 10), new Scalar(0, 0, 255), 2);
                        TextView textSerial = binding.textSerial;
                        TextView textDan = binding.textDan;
                        TextView textSTT = binding.textSTT;
                        TextView textLoai = binding.textLoai;
                        TextView textTai = binding.textTai;
                        TextView textSaiSo = binding.textSaiSo;

                        textSerial.setText("Serial: " + config.getSerial());
                        textDan.setText("Dàn: " + config.getStaging());
                        textSTT.setText("Stt: " + config.getStt());
                        textLoai.setText("Loại: " + config.getType());
                        textTai.setText("Tải: " + config.getTai());
                        textSaiSo.setText("V chuẩn: " + config.getSsDhm());
                        displayProcessedImage(matRGB);
                    } catch (Exception e) {
                        Log.e("ImageAnalysis", "Error processing image", e);
                    } finally {
                        imageProxy.close();
                    }
                });


                // Chọn camera sau
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Bind use cases to lifecycle
                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, imageAnalysis);

                // Lấy đối tượng CameraControl để điều chỉnh zoom
                cameraControl = camera.getCameraControl();

                // Thiết lập sự kiện nhấn nút
                setupZoomButtons();

            } catch (ExecutionException | InterruptedException ignored) {
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }


    // Hàm cập nhật số vòng quay
    public void updateRotationCount(double currentAngle, long currentTimeMillis) {
        // Tính sự thay đổi góc quay so với lần trước
        config.setAngleDifference(currentAngle - config.getPreviousAngle());

        // Xử lý trường hợp góc quay vượt qua 360° hoặc giảm xuống dưới 0°
        if (config.getAngleDifference() > 180) {
            config.setAngleDifference(config.getAngleDifference() - 360); // Quay ngược chiều kim đồng hồ
        } else if (config.getAngleDifference() < -180) {
            config.setAngleDifference(config.getAngleDifference() + 360); // Quay theo chiều kim đồng hồ
        }

        // Cộng dồn sự thay đổi góc quay vào tổng góc quay
        config.setTotalRotation(config.getTotalRotation() + config.getAngleDifference());

        // Cập nhật góc quay trước đó
        config.setPreviousAngle(currentAngle);

        // Cập nhật số vòng quay (bao gồm cả phần thập phân)
        double round = config.getTotalRotation() / 360;
        config.setRound(round);



        // Kiểm tra sự ổn định của số vòng quay và tính giá trị trung bình nếu ổn định
        if (isStable()) {
            // Cập nhật giá trị round trung bình khi ổn định
            double stableRoundAverage = calculateStableRoundAverage(recentRoundValues);
            config.setRound(stableRoundAverage);  // Cập nhật giá trị round trung bình
        }
    }


    // Hàm kiểm tra sự ổn định của giá trị round (ngưỡng thay đổi)
    private boolean isStable() {
        if (recentRoundValues.size() < 3) {
            return false; // Chưa đủ 3 giá trị để kiểm tra sự ổn định
        }

        // Kiểm tra sự thay đổi giữa 3 vòng quay gần nhất
        Double[] roundValues = recentRoundValues.toArray(new Double[0]);
        for (int i = 0; i < 2; i++) {
            if (Math.abs(roundValues[i] - roundValues[i + 1]) > THRESHOLD) { // Thay đổi lớn hơn ngưỡng
                return false;
            }
        }

        return true; // Nếu tất cả sự thay đổi nhỏ hơn ngưỡng, trả về true
    }

    // Hàm tính trung bình của các giá trị round gần nhất khi ổn định
    private double calculateStableRoundAverage(Queue<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    // Hàm tính lưu lượng từ sự thay đổi của các vòng quay gần nhất và thời gian tương ứng
    private double calculateFlow(Queue<Double> recentRoundValues, Queue<Long> timestamps) {
        if (recentRoundValues.size() < 2) {
            return 0; // Không đủ 2 giá trị để tính lưu lượng
        }

        // Lấy các giá trị vòng quay và thời gian từ hàng đợi
        Double[] roundValues = recentRoundValues.toArray(new Double[0]);
        Long[] timeStamps = timestamps.toArray(new Long[0]);

        // Tính sự thay đổi vòng quay và thời gian giữa hai lần cập nhật
        double totalChange = 0;
        double totalTime = 0; // Thời gian thay đổi (tính bằng giây)

        for (int i = 0; i < roundValues.length - 1; i++) {
            double roundChange = Math.abs(roundValues[i] - roundValues[i + 1]);
            long timeDiff = timeStamps[i + 1] - timeStamps[i]; // Chênh lệch thời gian giữa hai lần cập nhật

            totalChange += roundChange;
            totalTime += timeDiff / 1000.0; // Chuyển đổi từ mili giây sang giây
        }

        // Tính lưu lượng (tổng sự thay đổi vòng quay chia cho tổng thời gian)
        if (totalTime > 0) {
            return totalChange / totalTime;
        }

        return 0; // Nếu không có thay đổi thời gian hợp lệ, trả về 0
    }

    @SuppressLint("SetTextI18n")
    private void updateUIWithMQTT() {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        int textColorOld = (config.getCorrectionOld() < -1.5 || config.getCorrectionOld() > 1.5) ? Color.RED : Color.GREEN;
        int textColorOld1 = (config.getCorrectionOld1() < -1.5 || config.getCorrectionOld1() > 1.5) ? Color.RED : Color.GREEN;
        int textColorOld2 = (config.getCorrectionOld2() < -1.5 || config.getCorrectionOld2() > 1.5) ? Color.RED : Color.GREEN;
        TextView textViewLuongNuocOld = binding.textViewLuongNuocValueOld;
        TextView textViewChenhLechOld = binding.textViewChenhLechValueOld;
        TextView textViewSaiSoOld = binding.textViewSaiSoValueOld;
        TextView textViewLuongNuocOld1 = binding.textViewLuongNuocValueOld1;
        TextView textViewChenhLechOld1 = binding.textViewChenhLechValueOld1;
        TextView textViewSaiSoOld1 = binding.textViewSaiSoValueOld1;
        TextView textViewLuongNuocOld2 = binding.textViewLuongNuocValueOld2;
        TextView textViewChenhLechOld2 = binding.textViewChenhLechValueOld2;
        TextView textViewSaiSoOld2 = binding.textViewSaiSoValueOld2;
        textViewLuongNuocOld.setText(decimalFormat.format(config.getRoundOld()) + " Lít");
        textViewChenhLechOld.setText(decimalFormat.format(config.getFalseValueMeterOld()) + " Lít");
        textViewSaiSoOld.setText(decimalFormat.format(config.getCorrectionOld()) + " %");
        textViewLuongNuocOld1.setText(decimalFormat.format(config.getRoundOld1()) + " Lít");
        textViewChenhLechOld1.setText(decimalFormat.format(config.getFalseValueMeterOld1()) + " Lít");
        textViewSaiSoOld1.setText(decimalFormat.format(config.getCorrectionOld1()) + " %");
        textViewLuongNuocOld2.setText(decimalFormat.format(config.getRoundOld2()) + " Lít");
        textViewChenhLechOld2.setText(decimalFormat.format(config.getFalseValueMeterOld2()) + " Lít");
        textViewSaiSoOld2.setText(decimalFormat.format(config.getCorrectionOld2()) + " %");
        textViewChenhLechOld.setTextColor(textColorOld);
        textViewSaiSoOld.setTextColor(textColorOld);
        textViewChenhLechOld1.setTextColor(textColorOld1);
        textViewSaiSoOld1.setTextColor(textColorOld1);
        textViewChenhLechOld2.setTextColor(textColorOld2);
        textViewSaiSoOld2.setTextColor(textColorOld2);
        if (config.getIsStart()) {
            TextView textView6 = binding.textViewLuongNuocValueNew;
            textView6.setText(decimalFormat.format(config.getRound()) + " Lít");
        }
        if (config.getIsStart()) {
            TextView textView7 = binding.textViewChenhLechValueNew;
            TextView textView9 = binding.textViewSaiSoValueNew;
            // Tính toán
            double VChuan = config.getSsDhm();
            double round = config.getRound();

            double falseValue = round - VChuan;
            config.setFalseValueMeter(falseValue);

            double correction = 0;
            double ratioValue = 0;

            if (VChuan != 0) {
                correction = falseValue * 100 / VChuan;
                ratioValue = (falseValue / round) * 100;
            }
            config.setCorrection(correction);
            config.setRatio(ratioValue);
            // ((( ss đhm /100 ) + 1 ) * (tỉ lệ sai lệch đh Mẫu với đh kiểm / 100 + 1) -1)* 100
            config.setRatio(ratioValue);
            config.setCorrection(correction);

            int textColor = (correction < -1.5 || correction > 1.5) ? Color.RED : Color.GREEN;

            textView7.setText(decimalFormat.format(config.getFalseValueMeter()) + " Lít");
            textView9.setText(decimalFormat.format(correction) + "%");

            textView7.setTextColor(textColor);

            textView9.setTextColor(textColor);
        }
    }

    /**
     * Hàm hỗ trợ thiết lập dữ liệu cho TextView
     */
    private void setTextView(TextView textView, double value, String unit) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        textView.setText(decimalFormat.format(value) + unit);
    }

    /**
     * Hàm hỗ trợ thiết lập dữ liệu và màu cho TextView
     */
    private void setTextView(TextView textView, double value, String unit, int textColor) {
        setTextView(textView, value, unit);
        textView.setTextColor(textColor);
    }


    // Hàm tính khoảng cách giữa hai điểm
    public double distanceCalculate(Point p1, Point p2) {
        return Math.sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y));
    }
    private void setupZoomButtons() {
        Button buttonX1 = requireView().findViewById(R.id.buttonX1);
        Button buttonX2 = requireView().findViewById(R.id.buttonX2);
        Button buttonX5 = requireView().findViewById(R.id.buttonX5);
        Button buttonX10 = requireView().findViewById(R.id.buttonX10);

        buttonX1.setOnClickListener(v -> setZoom(1.0f));
        buttonX2.setOnClickListener(v -> setZoom(2.0f));
        buttonX5.setOnClickListener(v -> setZoom(5.0f));
        buttonX10.setOnClickListener(v -> setZoom(10.0f));
    }

    private void setZoom(float zoomRatio) {
        if (cameraControl != null) {
            cameraControl.setZoomRatio(zoomRatio);
        }
    }
    private Mat imageProxyToMat(ImageProxy imageProxy) {
        // Lấy các planes từ ImageProxy
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer(); // Y plane
        ByteBuffer uBuffer = planes[1].getBuffer(); // U plane
        ByteBuffer vBuffer = planes[2].getBuffer(); // V plane

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();


        // Tạo mảng byte để lưu dữ liệu NV21
        byte[] nv21 = new byte[ySize + uSize + vSize];

        // Copy dữ liệu Y, V, U vào mảng NV21
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        // Tạo Mat từ dữ liệu NV21
        Mat yuvMat = new Mat(imageProxy.getHeight() + imageProxy.getHeight() / 2, imageProxy.getWidth(), CvType.CV_8UC1);
        yuvMat.put(0, 0, nv21);

        // Chuyển đổi từ YUV (NV21) sang RGB
        Mat rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);
        Mat rotatedMat = new Mat();
        Core.rotate(rgbMat, rotatedMat, Core.ROTATE_90_CLOCKWISE);
        return rotatedMat;
    }

    private void displayProcessedImage(Mat mat) {
        // Convert rotated Mat to Bitmap
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);

        // Display the processed image in ImageView
        requireActivity().runOnUiThread(() -> {
            binding.imageView2.setVisibility(View.VISIBLE); // Show processed image
            binding.imageView2.setImageBitmap(bitmap);      // Set image to ImageView
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (config.getIsStart() && !"Kiểm".equals(config.getType())) {
            config.setStart(false); // Đảm bảo isStart được đặt về false khi View bị hủy
            mqtt.sendMQTTCommand(mqtt, "COMMAND=2",requireActivity());
            Log.d("ToggleEndButton", "Đang dừng vòng lặp");
        }
        isRunning = false;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        binding = null;
    }
}
