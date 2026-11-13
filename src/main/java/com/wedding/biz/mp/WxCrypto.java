package com.wedding.biz.mp;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/** 微信消息签名与 AES 解密（安全模式）。 */
public final class WxCrypto {
    private WxCrypto() {
    }

    public static boolean checkSignature(String token, String timestamp, String nonce, String signature) {
        if (token == null || timestamp == null || nonce == null || signature == null) return false;
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(String.join("", arr).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static String decrypt(String encodingAesKey, String appId, String encrypted) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
            byte[] data = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16)));
            byte[] plain = cipher.doFinal(data);
            int pad = plain[plain.length - 1] & 0xff;
            int len = plain.length - pad;
            byte[] content = Arrays.copyOfRange(plain, 16, len);
            int msgLen = ((content[0] & 0xff) << 24) | ((content[1] & 0xff) << 16)
                    | ((content[2] & 0xff) << 8) | (content[3] & 0xff);
            String msg = new String(content, 4, msgLen, StandardCharsets.UTF_8);
            String fromAppId = new String(content, 4 + msgLen, content.length - 4 - msgLen, StandardCharsets.UTF_8);
            if (!fromAppId.equals(appId)) throw new IllegalArgumentException("appId 不匹配");
            return msg;
        } catch (Exception e) {
            throw new IllegalArgumentException("微信消息解密失败", e);
        }
    }
}
