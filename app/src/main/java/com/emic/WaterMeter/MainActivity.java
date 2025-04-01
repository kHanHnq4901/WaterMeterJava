package com.emic.watermeter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.emic.watermeter.databinding.ActivityMainBinding;
import com.emic.watermeter.ui.ConfigManager;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.opencv.android.OpenCVLoader;

// Import cho In-App Update API:
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppUpdateManager appUpdateManager;
    private static final int MY_REQUEST_CODE = 123; // Mã request cập nhật

    // Listener theo dõi trạng thái update
    private final InstallStateUpdatedListener installStateUpdatedListener = state -> {
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // Khi update đã tải xong, hiển thị hộp thoại thông báo cho người dùng
            popupCompleteUpdate();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Ẩn ActionBar nếu có
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        BottomNavigationView navView = findViewById(R.id.nav_view);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        // Setup bottom navigation (không dùng ActionBar)
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Ẩn BottomNavigationView khi login
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.navigation_login) {
                binding.navView.setVisibility(View.GONE);
            } else {
                binding.navView.setVisibility(View.VISIBLE);
            }
        });

        // Giữ màn hình luôn sáng
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Khởi tạo Config
        ConfigManager.initialize(this);

        // Kiểm tra OpenCV
        if (OpenCVLoader.initDebug()) {
            Log.d("LOADED", "OpenCV loaded successfully");
        } else {
            Log.d("LOADED", "OpenCV load failed");
        }

        // Kiểm tra và bắt cập nhật ứng dụng
        checkForAppUpdate();

        // Yêu cầu cấp quyền camera
        requestCameraPermission();
    }

    /**
     * Kiểm tra cập nhật ứng dụng từ Google Play theo kiểu FLEXIBLE.
     * Ở đây không cho phép "Để sau", người dùng phải bắt cập nhật.
     */
    private void checkForAppUpdate() {
        // Tạo instance AppUpdateManager
        appUpdateManager = AppUpdateManagerFactory.create(this);
        // Đăng ký listener để theo dõi trạng thái cập nhật
        appUpdateManager.registerListener(installStateUpdatedListener);

        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            // Nếu có cập nhật và cho phép cập nhật kiểu FLEXIBLE
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                // Không hiển thị nút "Để sau" mà buộc người dùng cập nhật ngay
                try {
                    appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.FLEXIBLE,
                            MainActivity.this,
                            MY_REQUEST_CODE
                    );
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Hiển thị hộp thoại thông báo cho người dùng rằng update đã tải xong
     * và yêu cầu đóng ứng dụng để hoàn tất quá trình cập nhật.
     */
    private void popupCompleteUpdate() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Cập nhật hoàn tất")
                .setMessage("Ứng dụng đã được cập nhật thành công. Vui lòng đóng ứng dụng và mở lại để hoàn tất quá trình cập nhật.")
                .setPositiveButton("Đóng ứng dụng", (dialog, which) -> {
                    // Gọi completeUpdate() để cài đặt bản cập nhật và khởi động lại ứng dụng
                    appUpdateManager.completeUpdate();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Log.e("Update", "Cập nhật không thành công, resultCode: " + resultCode);
                // Có thể thêm logic để thông báo cho người dùng và thử lại cập nhật
                // Ví dụ: bạn có thể buộc cập nhật lại
                checkForAppUpdate();
            }
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            Log.d("PERMISSION", "Camera permission already granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("PERMISSION", "Camera permission granted");
            } else {
                Log.d("PERMISSION", "Camera permission denied");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Hủy đăng ký listener khi không cần thiết nữa để tránh rò rỉ bộ nhớ
        if (appUpdateManager != null) {
            appUpdateManager.unregisterListener(installStateUpdatedListener);
        }
    }
}
