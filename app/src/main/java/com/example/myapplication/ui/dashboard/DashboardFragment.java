package com.example.myapplication.ui.dashboard;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentDashboardBinding;

import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.example.myapplication.ui.Config;
import com.example.myapplication.ui.ConfigManager;
import com.example.myapplication.ui.Mqtt;
import com.example.myapplication.ui.home.HomeFragment;
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class DashboardFragment extends HomeFragment {
    private FragmentDashboardBinding binding;

    protected com.example.myapplication.ui.Mqtt mqtt;
    private static Config config;
    private SharedPreferences sharedPreferences;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        config = ConfigManager.getConfig();
        mqtt = Mqtt.getInstance();
        setupRadioButtons();
        setupSeekBar();
        setupUIVisibility();
        Context context = requireContext();
        // Button Setup
        // Nút "Làm Mới"
        binding.buttonLamMoi.setOnClickListener(v -> {
            // Tạo AlertDialog để hỏi người dùng có chắc chắn muốn làm mới không
            new AlertDialog.Builder(context)
                    .setTitle("Xác nhận")
                    .setMessage("Bạn có chắc chắn muốn làm mới không? Tất cả dữ liệu sẽ bị reset.")
                    .setPositiveButton("Có", (dialog, which) -> {
                        // Nếu người dùng chọn "Có", thực hiện làm mới và gửi lệnh MQTT
                        resetValues();
                        mqtt.sendMQTTCommand(mqtt, "COMMAND=3");
                    })
                    .setNegativeButton("Không", (dialog, which) -> {
                        // Nếu người dùng chọn "Không", không làm gì cả
                        dialog.dismiss();
                    })
                    .show();
        });

        // Nút "Lưu"
        binding.buttonLuu.setOnClickListener(v -> {
            // Tạo AlertDialog để hỏi người dùng có chắc chắn muốn lưu không
            new AlertDialog.Builder(context)
                    .setTitle("Xác nhận")
                    .setMessage("Bạn có chắc chắn muốn lưu không?")
                    .setPositiveButton("Có", (dialog, which) -> {
                        // Nếu người dùng chọn "Có", thực hiện lưu và gửi lệnh MQTT
                        saveCurrentConfig();
                        mqtt.sendMQTTCommand(mqtt, "COMMAND=4");
                    })
                    .setNegativeButton("Không", (dialog, which) -> {
                        // Nếu người dùng chọn "Không", không làm gì cả
                        dialog.dismiss();
                    })
                    .show();
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


        startCamera();
        return root;
    }

    private void setupRadioButtons() {
        RadioButton[] radioButtons = {
                binding.radioButtonQI, binding.radioButtonQII,
                binding.radioButtonQIII, binding.radioButtonQ3
        };
        String[] taiOptions = {"QI", "QII", "QIII", "Q3"};

        // Set initial state for the radio buttons based on config.getTai()
        for (int i = 0; i < radioButtons.length; i++) {
            if (taiOptions[i].equals(config.getTai())) {
                radioButtons[i].setChecked(true);
                break;
            }
        }

        // Set click listeners for each radio button
        for (int i = 0; i < radioButtons.length; i++) {
            final String tai = taiOptions[i];
            final double errValue = getErrValue(tai) != null ? Double.parseDouble(getErrValue(tai)) : 0.0;


            radioButtons[i].setOnClickListener(v -> onRadioButtonClicked(tai, errValue));
        }
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
        config.setSsDhm(String.valueOf(errValue));
        mqtt.sendMQTTCommand(mqtt,"ERROR=" + errValue);
        mqtt.sendMQTTCommand(mqtt,"TAI=" + tai);
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

        // Cập nhật giao diện người dùng
        TextView textView6 = binding.textViewLuongNuocValueOld2;
        textView6.setText(String.valueOf(config.getRound()));
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
        mqtt.sendMQTTCommand(mqtt, "COMMAND=1");
        config.setStart(true);
        isRunning = true;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                mqtt.sendMQTTCommand(mqtt, "ROUND=" + config.getRound());
            }
        }, 0, 1000); // Gửi lệnh mỗi 1 giây

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
        mqtt.sendMQTTCommand(mqtt, "COMMAND=2");
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
    Scalar lowRed = new Scalar(0,50,20);
    Scalar highRed = new Scalar(10, 255, 255);
    Scalar lowRed1 = new Scalar(150, 50, 20);
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

                        for (MatOfPoint c : contours) {
                            double contourArea = Imgproc.contourArea(c);

                            if (contourArea > 3000) {
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

                                // Kiểm tra khoảng cách đến center
                                if (distanceCalculate(center, centroid) < 400 && contourArea > maxArea) {
                                    maxArea = contourArea;
                                    bestContour = c; // Lưu contour lớn nhất thỏa mãn
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

                        String serial = "Serial: " + config.getSerial();
                        String dan = "Dan: " + config.getStaging();
                        String stt = "Stt: " + config.getStt();
                        String loai = "Loai: " + (config.getType().equals("Kiểm") ? "Kiem" : "Mau");
                        String tai = "Tai: " + config.getTai();
                        String saiso = "Sai So dh Mau: "+ config.getSsDhm();

                        // Định nghĩa các điểm và thông số vẽ
                        Point pointSerial = new Point(10, 100);
                        Point pointStt = new Point(10, 115);
                        Point pointDan = new Point(10, 130);
                        Point pointLoai = new Point(10, 145);
                        Point pointTai = new Point(10, 160);
                        Point pointSaiso = new Point(10, 175);

                        Scalar whiteColor = new Scalar(255, 255, 255);  // Màu trắng cho nền
                        Scalar blackColor = new Scalar(0, 0, 0);        // Màu đen cho chữ

                        // Vẽ nền trắng (hình chữ nhật)
                        Imgproc.rectangle(matRGB, new Point(5, 90), new Point(300, 110), whiteColor, -1); // serial
                        Imgproc.rectangle(matRGB, new Point(5, 110), new Point(300, 125), whiteColor, -1); // serial
                        Imgproc.rectangle(matRGB, new Point(5, 125), new Point(300, 140), whiteColor, -1); // dan
                        Imgproc.rectangle(matRGB, new Point(5, 140), new Point(300, 155), whiteColor, -1); // loai
                        Imgproc.rectangle(matRGB, new Point(5, 145), new Point(300, 170), whiteColor, -1); // tai
                        Imgproc.rectangle(matRGB, new Point(5, 170), new Point(300, 185), whiteColor, -1); // saiso

                            // Vẽ chữ màu đen lên nền trắng
                        Imgproc.putText(matRGB, serial, pointSerial, Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, blackColor, 1);
                        Imgproc.putText(matRGB, stt, pointStt, Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, blackColor, 1);
                        Imgproc.putText(matRGB, dan, pointDan, Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, blackColor, 1);
                        Imgproc.putText(matRGB, loai, pointLoai, Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, blackColor, 1);
                        Imgproc.putText(matRGB, tai, pointTai, Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, blackColor, 1);
                        Imgproc.putText(matRGB, saiso, pointSaiso, Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, blackColor, 1);
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
        int textColorOld = (config.getCorrectionOld() < -1 || config.getCorrectionOld() > 1) ? Color.RED : Color.GREEN;
        TextView textViewLuongNuocOld = binding.textViewLuongNuocValueOld2;
        TextView textViewChenhLechOld = binding.textViewChenhLechValueOld2;
        TextView textViewTiLeOld = binding.textViewTiLeValueOld2;
        TextView textViewSaiSoOld = binding.textViewSaiSoValueOld2;
        textViewLuongNuocOld.setText(decimalFormat.format(config.getRoundOld())+ " Lít");
        textViewChenhLechOld.setText(decimalFormat.format(config.getFalseValueMeterOld())+ " Lít");
        textViewTiLeOld.setText(decimalFormat.format(config.getRatioOld()) + " %");
        textViewSaiSoOld.setText(decimalFormat.format(config.getCorrectionOld())+ " %");

        textViewChenhLechOld.setTextColor(textColorOld);
        textViewTiLeOld.setTextColor(textColorOld);
        textViewSaiSoOld.setTextColor(textColorOld);
        if (config.getIsStart()) {
            TextView textView6 = binding.textViewLuongNuocValueNew;
            textView6.setText(decimalFormat.format(config.getRound())+ " Lít");
        }
        if ("Mẫu".equals(config.getType())) {
            return;
        }

        if (config.getIsStart()) {
            TextView textView7 = binding.textViewChenhLechValueNew;
            TextView textView8 = binding.textViewTiLeValueNew;
            TextView textView9 = binding.textViewSaiSoValueNew;

            // Tính toán
            config.setFalseValueMeter(config.getRound() - config.getValueMau());
            double ratioValue = (config.getFalseValueMeter() / config.getRound()) * 100;
            double ssDhm = Double.parseDouble(config.getSsDhm());
            double correction = (((ssDhm / 100) + 1) * (ratioValue / 100 + 1) - 1) * 100;
            // ((( ss đhm /100 ) + 1 ) * (tỉ lệ sai lệch đh Mẫu với đh kiểm / 100 + 1) -1)* 100
            config.setRatio(ratioValue);
            config.setCorrection(correction);

            int textColor = (correction < -1 || correction > 1) ? Color.RED : Color.GREEN;

            textView7.setText(decimalFormat.format(config.getFalseValueMeter())+ " Lít");
            textView8.setText(decimalFormat.format(ratioValue) + "%");
            textView9.setText(decimalFormat.format(correction) + "%");

            textView7.setTextColor(textColor);
            textView8.setTextColor(textColor);
            textView9.setTextColor(textColor);
        }
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

        if (config.getIsStart()) {
            config.setStart(false); // Đảm bảo isStart được đặt về false khi View bị hủy
            mqtt.sendMQTTCommand(mqtt, "COMMAND=2");
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
