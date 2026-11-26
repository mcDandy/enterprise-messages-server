package cz.upce.fei.ems.backend.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and validates signed authentication tokens.
 */

@Slf4j
@Service
public class JwtService {

    @Value("${JWT_SECRET:nějáký-Docela-dlouhý-klutz+*Kteryj7Saguestovatblbě6bude}")
    private String secretKey;

    @Value("${JWT_EXPIRATION:86400000}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Signs a token whose subject is the supplied username and whose lifetime is configured by {@code JWT_EXPIRATION}.
     *
     * @param username token subject
     * @return compact signed JWT
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the subject after verifying the token signature.
     *
     * @param token compact JWT to parse
     * @return token subject, or {@code null} when parsing or signature validation fails
     */
    public String extractUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to extract username from JWT token: {}", e.getMessage());
            return null;
        }
    }

    /** @return whether the token has a valid signature and has not expired */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** @return whether the token is valid and its subject matches the loaded account */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username != null && username.equals(userDetails.getUsername()) && validateToken(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** @return expiration time from the signed token, expressed as epoch milliseconds */
    public long getExpirationTimestamp(String token) {
        return extractExpiration(token).getTime();
    }

    private Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }
}
