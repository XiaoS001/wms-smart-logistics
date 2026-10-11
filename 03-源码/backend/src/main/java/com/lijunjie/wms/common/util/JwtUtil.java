package com.lijunjie.wms.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 令牌工具。
 *
 * <p>登录成功后签发令牌，后续请求通过 {@code Authorization: Bearer <token>} 携带。
 * 密钥与有效期从配置文件读取（{@code wms.jwt.*}），生产环境应通过环境变量覆盖。</p>
 */
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLES = "roles";

    @Value("${wms.jwt.secret}")
    private String secret;

    @Value("${wms.jwt.expire-hours:12}")
    private long expireHours;

    /**
     * 签发令牌。
     *
     * @param userId   用户 ID
     * @param username 登录名
     * @param roles    角色编码列表
     * @return JWT 字符串
     */
    public String generate(Long userId, String username, List<String> roles) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ROLES, roles);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expireHours * 3600_000L))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /**
     * 解析令牌。
     *
     * @param token JWT 字符串
     * @return 载荷；令牌非法或过期时返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从令牌中取用户 ID */
    public Long getUserId(String token) {
        Claims claims = parse(token);
        if (claims == null) {
            return null;
        }
        Object v = claims.get(CLAIM_USER_ID);
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    /** 从令牌中取登录名 */
    public String getUsername(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    /** 令牌有效期（小时），供登录响应返回给前端 */
    public long getExpireHours() {
        return expireHours;
    }
}
