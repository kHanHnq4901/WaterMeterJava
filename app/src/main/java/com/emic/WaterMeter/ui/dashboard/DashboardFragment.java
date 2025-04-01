package com.emic.watermeter.ui.dashboard;

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

import com.emic.watermeter.R;
import com.emic.watermeter.databinding.FragmentDashboardBinding;

import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.emic.watermeter.ui.Config;
import com.emic.watermeter.ui.ConfigManager;
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class DashboardFragment extends HomeFragment {
    private FragmentDashboardBinding binding;

    protected com.emic.watermeter.ui.Mqtt mqtt;
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
        // Button Setup
        // Nút "Làm Mới"
        binding.buttonLamMoi.setOnClickListener(v -> {
            resetValues();
            mqtt.sendMQTTCommand(mqtt, "COMMAND=3");
        });

        // Nút "Lưu"
        binding.buttonLuu.setOnClickListener(v -> {
            saveCurrentConfig();
            mqtt.sendMQTTCommand(mqtt, "COMMAND=4");
        });

        // Nút "Lưu Excel"
        binding.buttonLuuExcel.setOnClickListener(v -> {
            saveCurrentConfig();
            mqtt.sendMQTTCommand(mqtt, "COMMAND=5");
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
        // Lưu giá trị Old1 vào biến tạm trước khi cập nhật
        double roundOld1Temp = config.getRoundOld1();
        double falseValueMeterOld1Temp = config.getFalseValueMeterOld1();
        double ratioOld1Temp = config.getRatioOld1();
        double correctionOld1Temp = config.getCorrectionOld1();

        // Cập nhật Old2 trước
        config.setRoundOld2(roundOld1Temp);
        config.setFalseValueMeterOld2(falseValueMeterOld1Temp);
        config.setRatioOld2(ratioOld1Temp);
        config.setCorrectionOld2(correctionOld1Temp);

        // Kiểm tra xem Old2 đã cập nhật đúng chưa
        if (config.getRoundOld2() == roundOld1Temp &&
                config.getFalseValueMeterOld2() == falseValueMeterOld1Temp &&
                config.getRatioOld2() == ratioOld1Temp &&
                config.getCorrectionOld2() == correctionOld1Temp) {

            // Nếu Old2 cập nhật xong hết, mới cập nhật Old1
            config.setRoundOld1(config.getRound());
            config.setFalseValueMeterOld1(config.getFalseValueMeter());
            config.setRatioOld1(config.getRatio());
            config.setCorrectionOld1(config.getCorrection());
        } else {
            System.err.println("Error: Old2 values were not updated correctly. Aborting Old1 update.");
        }
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
                        textSaiSo.setText("Sai số dhm: " + config.getSsDhm());
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

        // Lấy màu dựa trên điều kiện
        int textColorOld1 = (config.getCorrectionOld1() < -1.5 || config.getCorrectionOld1() > 1.5) ? Color.RED : Color.GREEN;
        int textColorOld2 = (config.getCorrectionOld2() < -1.5 || config.getCorrectionOld2() > 1.5) ? Color.RED : Color.GREEN;

        // Thiết lập dữ liệu cho các TextView
        setTextView(binding.textViewLuongNuocValueOld1, config.getRoundOld1(), " Lít");
        setTextView(binding.textViewChenhLechValueOld1, config.getFalseValueMeterOld1(), " Lít", textColorOld1);
        setTextView(binding.textViewSaiSoValueOld1, config.getCorrectionOld1(), " %", textColorOld1);

        setTextView(binding.textViewLuongNuocValueOld2, config.getRoundOld2(), " Lít");
        setTextView(binding.textViewChenhLechValueOld2, config.getFalseValueMeterOld2(), " Lít", textColorOld2);
        setTextView(binding.textViewSaiSoValueOld2, config.getCorrectionOld2(), " %", textColorOld2);

        // Nếu đang chạy, cập nhật dữ liệu mới
        if (config.getIsStart()) {
            setTextView(binding.textViewLuongNuocValueNew, config.getRound(), " Lít");
        }

        // Nếu loại là "Mẫu" thì không cần xử lý tiếp
        if ("Mẫu".equals(config.getType())) {
            return;
        }

        // Nếu đang chạy, tính toán và cập nhật dữ liệu mới
        if (config.getIsStart()) {
            // Tính toán giá trị sai số
            config.setFalseValueMeter(config.getRound() - config.getValueMau());
            double ratioValue = (config.getFalseValueMeter() / config.getRound()) * 100;
            double ssDhm = Double.parseDouble(config.getSsDhm());
            double correction = (((ssDhm / 100) + 1) * (ratioValue / 100 + 1) - 1) * 100;

            config.setRatio(ratioValue);
            config.setCorrection(correction);

            int textColor = (correction < -1.5 || correction > 1.5) ? Color.RED : Color.GREEN;

            setTextView(binding.textViewChenhLechValueNew, config.getFalseValueMeter(), " Lít", textColor);
            setTextView(binding.textViewSaiSoValueNew, correction, " %", textColor);
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
