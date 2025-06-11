package com.emic.watermeter.ui.home;

import android.app.AlertDialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.emic.watermeter.R;
import com.emic.watermeter.databinding.ActivityMainBinding;
import com.emic.watermeter.databinding.FragmentHomeBinding;
import com.emic.watermeter.ui.Config;
import com.emic.watermeter.ui.ConfigManager;
import com.emic.watermeter.ui.Mqtt;
import com.google.android.material.bottomnavigation.BottomNavigationView;


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private Mqtt mqtt;
    private Config config;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Chỉ dùng FragmentHomeBinding, không sử dụng ActivityMainBinding
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Lấy NavController
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main);

        // Cấu hình BottomNavigationView
        BottomNavigationView navView = requireActivity().findViewById(R.id.nav_view);

        NavigationUI.setupWithNavController(navView, navController);

        // Lấy SharedPreferences
        config = ConfigManager.getConfig();
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        config.setSerial(sharedPreferences.getString("serial", ""));
        config.setStaging(sharedPreferences.getString("staging", ""));
        config.setErrQI(sharedPreferences.getString("errQI", "0"));
        config.setErrQII(sharedPreferences.getString("errQII", "0"));
        config.setErrQIII(sharedPreferences.getString("errQIII", "0"));
        config.setErrQ3(sharedPreferences.getString("errQ3", "0"));
        config.setType(sharedPreferences.getString("type", "Kiểm"));
        config.setIp(sharedPreferences.getString("ip", "14.225.244.63"));
        config.setPort(sharedPreferences.getString("port", "2883"));
        // Lấy MQTT instance
        mqtt = Mqtt.getInstance(); // Singleton instance

        // Thiết lập giá trị ban đầu cho EditText
        // Gán trực tiếp từ SharedPreferences vào EditText
        binding.serialInput.setText(sharedPreferences.getString("serial", ""));
        binding.stagingInput.setText(sharedPreferences.getString("staging", ""));
        binding.ipInput.setText(sharedPreferences.getString("ip", "14.225.244.63"));
        binding.portInput.setText(sharedPreferences.getString("port", "2883"));

        // Chọn radio button phù hợp
        int radioButtonId = config.getType().equals("Kiểm") ? R.id.typeSample : R.id.typeCheck;
        binding.typeRadioGroup.check(radioButtonId);

        // Thêm sự kiện thay đổi giá trị radio button
        binding.typeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedRadioButton = binding.getRoot().findViewById(checkedId);
            String selectedType = selectedRadioButton.getText().toString();
            config.setType(selectedType);
            Log.d("RadioGroup", "Selected Type: " + selectedType);
        });

        // Thêm TextWatcher để cập nhật config khi nhập liệu
        setupTextWatchers();

        // Xử lý sự kiện kết nối
        binding.connectButton.setOnClickListener(v -> handleConnect());
        PackageInfo packageInfo = null;
        try {
            packageInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        String versionName = packageInfo.versionName;
        binding.versionText.setText("Phiên bản: " + versionName);
        return root;
    }

    private void setupTextWatchers() {
        binding.serialInput.addTextChangedListener(createTextWatcher(text -> config.setSerial(text)));
        binding.stagingInput.addTextChangedListener(createTextWatcher(text -> config.setStaging(text)));
    }

    private TextWatcher createTextWatcher(TextChangeListener listener) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                listener.onTextChanged(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
    }

    private interface TextChangeListener {
        void onTextChanged(String text);
    }



    private void handleConnect() {
        if (config == null) {
            Log.e("HomeFragment", "Config is null!");
            return;
        }

        // Cập nhật config từ UI
        config.setSerial(binding.serialInput.getText().toString());
        config.setStaging(binding.stagingInput.getText().toString());
        config.setIp(binding.ipInput.getText().toString());
        config.setPort(binding.portInput.getText().toString());
        int selectedId = binding.typeRadioGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedRadioButton = binding.getRoot().findViewById(selectedId);
            config.setType(selectedRadioButton.getText().toString());
        }

        if (isConfigInvalid()) {
            showAlertDialog();
        } else {
            ConfigManager.getInstance().saveConfigToPreferences();
            mqtt.connect(requireActivity());
        }
    }

    private boolean isConfigInvalid() {
        if ("Mẫu".equals(config.getType())) {
            return config.getSerial().isEmpty() || config.getStaging().isEmpty() ||
                    config.getErrQI().isEmpty() || config.getErrQII().isEmpty() ||
                    config.getErrQIII().isEmpty() || config.getErrQ3().isEmpty();
        } else if ("Kiểm".equals(config.getType())) {
            return config.getSerial().isEmpty() || config.getStaging().isEmpty();
        }
        return false;
    }

    private void showAlertDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Cảnh báo")
                .setMessage("Vui lòng nhập đầy đủ các giá trị trước khi kết nối.")
                .setPositiveButton("OK", (dialog, which) -> {})
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
