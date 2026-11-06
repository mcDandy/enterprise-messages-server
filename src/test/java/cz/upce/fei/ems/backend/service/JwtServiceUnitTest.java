package cz.upce.fei.ems.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceUnitTest {

    private JwtService jwtService;
    private final String secret = "test-secret-key-that-is-long-enough-for-hmac-sha-256";
    private final long expiration = 3600000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", secret);
        ReflectionTestUtils.setField(jwtService, "expiration", expiration);
    }

    @Test
    void testGenerateAndExtractUsername() {
        String username = "testuser";
        String token = jwtService.generateToken(username);
        assertNotNull(token);

        String extracted = jwtService.extractUsername(token);
        assertEquals(username, extracted);
    }

    @Test
    void testValidateToken() {
        String username = "testuser";
        String token = jwtService.generateToken(username);
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void testIsTokenValid() {
        String username = "testuser";
        String token = jwtService.generateToken(username);
        UserDetails userDetails = new User(username, "password", Collections.emptyList());

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void testIsTokenInvalidForDifferentUser() {
        String username = "testuser";
        String token = jwtService.generateToken(username);
        UserDetails userDetails = new User("otheruser", "password", Collections.emptyList());

        assertFalse(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtService.validateToken("invalid-token"));
        assertNull(jwtService.extractUsername("invalid-token"));
    }

    @Test
    void testExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);
        String token = jwtService.generateToken("testuser");
        assertFalse(jwtService.validateToken(token));
    }
}
