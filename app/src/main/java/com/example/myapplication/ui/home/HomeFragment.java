package com.example.myapplication.ui.home;

import android.app.AlertDialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.example.myapplication.ui.Config;
import com.example.myapplication.ui.ConfigManager;
import com.example.myapplication.ui.Mqtt;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private Mqtt mqtt;
    private Config config;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        config = ConfigManager.getConfig();
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        config.setSerial(sharedPreferences.getString("serial", ""));
        config.setStaging(sharedPreferences.getString("staging", "1"));
        config.setErrQI(sharedPreferences.getString("errQI", "0"));
        config.setErrQII(sharedPreferences.getString("errQII", "0"));
        config.setErrQIII(sharedPreferences.getString("errQIII", "0"));
        config.setErrQ3(sharedPreferences.getString("errQ3", "0"));
        config.setType(sharedPreferences.getString("type", "Kiểm"));
        mqtt = Mqtt.getInstance(); // Singleton instance
        // Thiết lập giá trị ban đầu
        binding.serialInput.setText(config.getSerial());
        binding.stagingInput.setText(config.getStaging());
        binding.qiInput.setText(config.getErrQI());
        binding.qiiInput.setText(config.getErrQII());
        binding.qiiiInput.setText(config.getErrQIII());
        binding.q3Input.setText(config.getErrQ3());

        // Chọn radio button phù hợp
        int radioButtonId = config.getType().equals("Kiểm") ? R.id.typeSample : R.id.typeCheck;
        binding.typeRadioGroup.check(radioButtonId);

        // Cập nhật hiển thị theo loại
        updateSaiSoSectionVisibility(config.getType());

        // Thêm sự kiện thay đổi giá trị radio button
        binding.typeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedRadioButton = binding.getRoot().findViewById(checkedId);
            String selectedType = selectedRadioButton.getText().toString();
            config.setType(selectedType);
            Log.d("RadioGroup", "Selected Type: " + selectedType);
            updateSaiSoSectionVisibility(selectedType);
        });

        // Thêm TextWatcher để cập nhật config khi nhập liệu
        setupTextWatchers();

        // Xử lý sự kiện kết nối
        binding.connectButton.setOnClickListener(v -> handleConnect());

        return root;
    }

    private void setupTextWatchers() {
        binding.serialInput.addTextChangedListener(createTextWatcher(text -> config.setSerial(text)));
        binding.stagingInput.addTextChangedListener(createTextWatcher(text -> config.setStaging(text)));
        binding.qiInput.addTextChangedListener(createTextWatcher(text -> config.setErrQI(text)));
        binding.qiiInput.addTextChangedListener(createTextWatcher(text -> config.setErrQII(text)));
        binding.qiiiInput.addTextChangedListener(createTextWatcher(text -> config.setErrQIII(text)));
        binding.q3Input.addTextChangedListener(createTextWatcher(text -> config.setErrQ3(text)));
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

    private void updateSaiSoSectionVisibility(String type) {
        if ("Kiểm".equals(type)) {
            binding.saiSoSection.setVisibility(View.GONE);
        } else {
            binding.saiSoSection.setVisibility(View.VISIBLE);
        }
    }

    private void handleConnect() {
        if (config == null) {
            Log.e("HomeFragment", "Config is null!");
            return;
        }

        // Cập nhật config từ UI
        config.setSerial(binding.serialInput.getText().toString());
        config.setStaging(binding.stagingInput.getText().toString());
        config.setErrQI(binding.qiInput.getText().toString());
        config.setErrQII(binding.qiiInput.getText().toString());
        config.setErrQIII(binding.qiiiInput.getText().toString());
        config.setErrQ3(binding.q3Input.getText().toString());

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
