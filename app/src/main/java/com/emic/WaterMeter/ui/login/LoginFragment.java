package com.emic.watermeter.ui.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.emic.watermeter.R;
import com.emic.watermeter.databinding.FragmentLoginBinding;

public class LoginFragment extends Fragment {
    private FragmentLoginBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Nếu đã đăng nhập trước đó, tự động chuyển đến HomeFragment
        if (isUserLoggedIn()) {
            binding.getRoot().post(() -> navigateToHome()); // Đảm bảo giao diện đã được tạo trước khi điều hướng
        }

        // Phát video nền
        setupVideoBackground();

        // Bắt sự kiện click nút đăng nhập
        binding.btnLogin.setOnClickListener(v -> {
            String inputCode = binding.editTextCode.getText().toString().trim();

            if (inputCode.equalsIgnoreCase("EMICDHN")) {
                saveLoginState(); // Lưu trạng thái đăng nhập
                navigateToHome();
            } else {
                Toast.makeText(getContext(), "Mã đăng nhập sai!", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }


    // Phát video nền toàn màn hình
    private void setupVideoBackground() {
        VideoView videoView = binding.videoBackground;
        String videoPath = "android.resource://" + requireActivity().getPackageName() + "/" + R.raw.background_video;

        videoView.setVideoURI(Uri.parse(videoPath));
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true); // Lặp lại video liên tục
            mp.setVolume(0f, 0f); // Tắt âm thanh
            videoView.start();
        });

        // Ẩn điều khiển video
        videoView.setMediaController(null);
    }

    // Lưu trạng thái đăng nhập vào SharedPreferences
    private void saveLoginState() {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.apply();
    }

    // Kiểm tra người dùng đã đăng nhập chưa
    private boolean isUserLoggedIn() {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

    // Chuyển đến màn hình chính
    private void navigateToHome() {
        if (getView() != null) {
            Navigation.findNavController(getView()).navigate(R.id.action_navigation_login_to_navigation_home);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
