package com.aiconsultant.consultant.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import static com.aiconsultant.consultant.utils.Constants.SALT;

public class PasswordUtils {

    /**
     * 对原始密码进行加盐哈希
     * @param rawPassword 原始明文密码
     * @return 加密后的十六进制字符串
     */
    public static String encode(String rawPassword) {
        try {
            // 拼接原始密码和盐值
            String saltedPassword = rawPassword + SALT;

            // 获取 SHA-256 实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));

            // 将字节数组转换为十六进制字符串
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("加密算法不存在", e);
        }
    }

    /**
     * 校验密码是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        String newHash = encode(rawPassword);
        return newHash.equals(encodedPassword);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}