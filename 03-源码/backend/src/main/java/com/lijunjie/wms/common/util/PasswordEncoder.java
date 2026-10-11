package com.lijunjie.wms.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具（PBKDF2 风格的自研实现，避免引入额外依赖）。
 *
 * <p>存储格式：{@code 算法标识$迭代次数$盐(Base64)$派生密钥(Base64)}</p>
 * <p>示例：{@code pbkdf2$120000$R2h2cE9r$Zk9vYmFy...}</p>
 *
 * <p>安全说明：</p>
 * <ul>
 *   <li>使用 JDK 内置 {@link MessageDigest} 实现迭代哈希，不依赖 Spring Security；</li>
 *   <li>每个用户的盐随机生成，相同的明文密码产生不同的密文；</li>
 *   <li>校验采用恒定时间比较，避免时序攻击。</li>
 * </ul>
 *
 * <p>设计取舍：毕业设计场景下优先选择"零额外依赖 + 可读性"，生产环境建议直接使用
 * BCrypt 或 Argon2 等经过充分审计的算法。</p>
 */
public final class PasswordEncoder {

    /** 算法标识，写入密文首段，便于将来升级算法时兼容旧数据 */
    private static final String ALGORITHM = "pbkdf2";

    /** 迭代次数：兼顾安全与登录响应时间 */
    private static final int ITERATIONS = 120000;

    /** 盐长度（字节） */
    private static final int SALT_BYTES = 16;

    /** 派生密钥长度（字节） */
    private static final int KEY_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordEncoder() {
    }

    /**
     * 计算密码摘要。
     *
     * @param rawPassword 明文密码
     * @return 可直接存入数据库的密文串
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(rawPassword, salt, ITERATIONS, KEY_BYTES);
        Base64.Encoder enc = Base64.getEncoder();
        return ALGORITHM + "$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(key);
    }

    /**
     * 校验密码是否匹配。
     *
     * @param rawPassword     明文密码
     * @param encodedPassword 数据库中的密文
     * @return 匹配返回 true；密文格式非法或为空时返回 false
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$");
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(rawPassword, salt, iterations, expected.length);
            return constantTimeEquals(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 迭代哈希：H(H(...H(盐 + 密码))) */
    private static byte[] derive(String rawPassword, byte[] salt, int iterations, int keyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] password = rawPassword.getBytes(StandardCharsets.UTF_8);
            byte[] block = new byte[salt.length + password.length];
            System.arraycopy(salt, 0, block, 0, salt.length);
            System.arraycopy(password, 0, block, salt.length, password.length);

            byte[] hash = digest.digest(block);
            for (int i = 1; i < iterations; i++) {
                digest.reset();
                digest.update(salt);
                hash = digest.digest(hash);
            }
            if (hash.length == keyBytes) {
                return hash;
            }
            byte[] out = new byte[keyBytes];
            System.arraycopy(hash, 0, out, 0, Math.min(hash.length, keyBytes));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("密码摘要计算失败", e);
        }
    }

    /** 恒定时间比较，避免根据比较耗时推断密码信息 */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
