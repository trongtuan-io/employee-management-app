package com.example.employee_management_app;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

// Xac thuc van tay bang BiometricPrompt: Android khong cho app doc du lieu
// van tay tho — van tay chi duoc doi chieu ngay tren thiet bi voi mau da
// dang ky trong Cai dat cua may. App nhan ve ket qua thanh cong/that bai
// roi moi goi API cham cong voi method="fingerprint".
public class BiometricHelper {

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public static boolean canUseFingerprint(AppCompatActivity activity) {
        BiometricManager bm = BiometricManager.from(activity);
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void authenticate(AppCompatActivity activity, String title, Callback cb) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        cb.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            cb.onError("Đã huỷ xác thực vân tay");
                        } else {
                            cb.onError("Xác thực vân tay lỗi: " + errString);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // 1 lan cham khong khop — hop thoai van mo de thu lai,
                        // chua coi la ket thuc nen khong bao gi o day.
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Chạm cảm biến vân tay để xác thực")
                .setNegativeButtonText("Huỷ")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        prompt.authenticate(info);
    }
}
