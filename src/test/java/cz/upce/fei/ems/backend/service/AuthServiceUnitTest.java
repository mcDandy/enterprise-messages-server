package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.UserKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private UserKeyRepository userKeyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequestDto registerRequest;
    private LoginRequestDto loginRequest;
    private AppUser user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password");
        registerRequest.setPublicKey("public-key");

        loginRequest = new LoginRequestDto();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        user = new AppUser();
        user.setId(1L);
        user.setUsername("testuser");
        user.setHashedPassword("hashed-password");
    }

    @Test
    void testRegisterSuccess() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(userRepository.save(any())).thenReturn(user);
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.getExpirationTimestamp(any())).thenReturn(123456L);

        AuthResponseDto response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("token", response.getToken());
        verify(userRepository).save(any());
        verify(userKeyRepository).save(any());
    }

    @Test
    void testRegisterConflict() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));
        assertThrows(ResponseStatusException.class, () -> authService.register(registerRequest));
    }

    @Test
    void testAuthenticateSuccess() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.getExpirationTimestamp(any())).thenReturn(123456L);

        AuthResponseDto response = authService.authenticate(loginRequest);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
    }

    @Test
    void testAuthenticateUserNotFound() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> authService.authenticate(loginRequest));
    }

    @Test
    void testAuthenticateWrongPassword() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> authService.authenticate(loginRequest));
    }

    @Test
    void testAuthenticateBanned() {
        user.setBanned(true);
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));
        assertThrows(SecurityException.class, () -> authService.authenticate(loginRequest));
    }

}
