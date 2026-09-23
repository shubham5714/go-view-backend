package cn.com.v2.util;

import cn.hutool.core.codec.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Helper for generating TOTP secrets and verifying codes (Google Authenticator compatible).
 */
public class TotpUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTES = 20; // 160 bits
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    /**
     * Generate a new random Base32-encoded secret key.
     */
    public static String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(buffer);
        return Base32.encode(buffer);
    }

    /**
     * Verify a numeric TOTP code against the shared Base32 secret.
     * Allows a small time drift window (+/- 1 step).
     */
    public static boolean verifyCode(String base32Secret, int code) {
        if (base32Secret == null || base32Secret.isEmpty()) {
            return false;
        }
        byte[] keyBytes = Base32.decode(base32Secret);
        long currentInterval = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;

        // allow previous, current, and next time-step codes
        for (int i = -1; i <= 1; i++) {
            long counter = currentInterval + i;
            int expected = generateTotp(keyBytes, counter);
            if (expected == code) {
                return true;
            }
        }
        return false;
    }

    private static int generateTotp(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(keySpec);

            // 8-byte big-endian counter
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }

            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return otp;
        } catch (Exception e) {
            return -1;
        }
    }
}

