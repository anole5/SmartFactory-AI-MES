package com.smartfactory.mes.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 工具（jjwt 0.12.x）
 *
 * <p>【踩坑】0.12 起 API 大改：旧的 parserBuilder()/setSigningKey()/getBody() 已删除，
 * 签发用 {@code Jwts.builder().signWith(key)}，解析用
 * {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}。</p>
 *
 * <p>Claims 设计：subject = 用户 ID，claim("username") = 登录名。
 * <b>权限标识不放进 token</b>——每次请求查库，权限变更即时生效，
 * 代价是每请求一次 join 查询（生产环境用 Redis 缓存，见 AuthInterceptor 注释）。</p>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-hours:24}") long expireHours) {
        // HS256 要求密钥至少 32 字节，过短会抛 WeakKeyException
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    /** 签发 token：subject=userId，过期时间 = 当前 + expireHours */
    public String generate(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 token。
     * 签名不符/格式错误抛 JwtException；过期抛 ExpiredJwtException（JwtException 子类）。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
