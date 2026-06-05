package wikirmi.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** Salted SHA-256 password hashing with a constant-time verify. */
public final class PasswordHasher {
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHasher() {}

    /** A fresh random 16-byte salt, hex-encoded. */
    public static String newSalt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return toHex(b);
    }

    /** SHA-256(salt-bytes || password-utf8), hex-encoded. */
    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(fromHex(salt));
            return toHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time comparison to avoid timing leaks. */
    public static boolean verify(String password, String salt, String expectedHash) {
        return MessageDigest.isEqual(fromHex(hash(password, salt)), fromHex(expectedHash));
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }

    private static byte[] fromHex(String h) {
        int n = h.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
