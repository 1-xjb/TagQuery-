package com.platform.tagquery.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 签名工具：HMAC-SHA256。
 *
 * 📖 签名规则（与调用方约定，蓝图 6.1 节）：
 *    sign = HMAC-SHA256( appKey + timestamp + ids用"|"拼接 + dataSourceId , appSecret )
 *
 * 🔐 安全 —— 这套签名在防三件事：
 * 1. 【防冒充】appSecret 只有平台和调用方知道，别人造不出合法签名；
 * 2. 【防篡改】签名内容包含 timestamp 和 ids，改任何一个参数签名就对不上；
 * 3. 【防重放】timestamp 参与签名 + 服务端校验时效（5 分钟），
 *    就算请求被抓包，5 分钟后也无法重放。
 *
 * ⚠️ 为什么不用 MD5(参数+secret) 拼接？—— 存在长度扩展攻击风险，HMAC 是标准做法。
 */

public class SignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public static String sign(String appkey , long timestamp , List<String>ids , String dataSourceId, String appSecret){
        String content = appkey + timestamp + String.join("|" , ids) + dataSourceId;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));

            return toHex(raw);

        } catch (Exception e) {
            throw new RuntimeException("签名计算失败", e);

        }

    }

    /**
     * 校验签名。
     *
     * 🔐 安全：必须用"常量时间比较"（MessageDigest.isEqual），
     *    不能用 String.equals —— equals 逐字符比较、发现不同立刻返回 false，
     *    攻击者可以通过响应耗时差异逐位猜出正确签名（计时攻击）。
     */
    public static boolean verify(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 字节数组转十六进制字符串 */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
