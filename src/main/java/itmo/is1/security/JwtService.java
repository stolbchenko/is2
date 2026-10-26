package itmo.is1.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@ApplicationScoped
public class JwtService {

    private static final long TTL_MILLIS = 24 * 60 * 60 * 1000L;

    private Key key;

    @PostConstruct
    void init() {
        String secret = System.getenv("IS1_JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "Переменная окружения IS1_JWT_SECRET не задана или короче 32 символов");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String username) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + TTL_MILLIS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String verifyAndGetUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
