package com.emic.watermeter.ui;

import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;

public class MultiModeAnalyzer implements ImageAnalysis.Analyzer {

    /** Callback nhận kết quả từ QR */
    public interface OnResultListener {
        void onDetected(@NonNull String value);
    }

    private final OnResultListener listener;
    private final BarcodeScanner qrScanner = BarcodeScanning.getClient();

    public MultiModeAnalyzer(@NonNull OnResultListener listener) {
        this.listener = listener;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        qrScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode bc : barcodes) {
                        String raw = bc.getRawValue();
                        if (raw != null && !raw.isEmpty()) {
                            listener.onDetected(raw); // Gửi kết quả QR
                            break;
                        }
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(t -> imageProxy.close());
    }
}
