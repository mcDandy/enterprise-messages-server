
package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RefreshTokenRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.UserKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implements account registration, password authentication, and refresh-token issuance.
 */

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final UserKeyRepository userKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Creates the account and initial public key in one transaction, then issues an access token.
     *
     * @param request validated registration data
     * @return the new account name and token metadata
     * @throws ResponseStatusException with {@code 409 Conflict} when the username is already taken
     */
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Username already exists"
            );
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setHashedPassword(passwordEncoder.encode(request.getPassword()));

        user = userRepository.save(user);

        UserKey userKey = new UserKey();
        userKey.setUserId(user.getId());
        userKey.setPublicKey(request.getPublicKey());

        userKeyRepository.save(userKey);

        String token = jwtService.generateToken(user.getUsername());

        return AuthResponseDto.fromToken(token, user.getUsername(), jwtService.getExpirationTimestamp(token));
    }

    /**
     * Verifies credentials and refuses authentication for globally banned accounts.
     *
     * @param request validated login credentials
     * @return a newly issued access token and its metadata
     * @throws ResponseStatusException when the account does not exist or the password is incorrect
     * @throws SecurityException when the account is banned
     */
    public AuthResponseDto authenticate(LoginRequestDto request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User doesn't exist"
                ));

        if (user.isBanned()) {
            throw new SecurityException("Access denied: Your account has been banned.");
        }

        if (!passwordEncoder.matches(
                request.getPassword(), user.getHashedPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Wrong password."
            );
        }

        String token = jwtService.generateToken(user.getUsername());

        return AuthResponseDto.fromToken(token, user.getUsername(), jwtService.getExpirationTimestamp(token));
    }

    /**
     * Validates a refresh token against the current user state and issues a replacement token.
     *
     * @param request validated refresh-token request
     * @return replacement token and expiration metadata
     * @throws ResponseStatusException when the token is invalid, expired, or belongs to no account
     */
    public AuthResponseDto refreshToken(RefreshTokenRequestDto request) {
        String token = request.getRefreshToken();
        String username = extractRefreshUsername(token);
        AppUser user = findRefreshUser(username);
        rejectBannedUser(user);
        UserDetails userDetails = loadRefreshUserDetails(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Expired or invalid refresh token"
            );
        }

        String newToken = jwtService.generateToken(userDetails.getUsername());

        return AuthResponseDto.fromToken(
                newToken,
                userDetails.getUsername(),
                jwtService.getExpirationTimestamp(newToken)
        );
    }

    private String extractRefreshUsername(String token) {
        String username = jwtService.extractUsername(token);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token format");
        }
        return username;
    }

    private AppUser findRefreshUser(String username) {
        try {
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "User does not exist"
                    ));
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "User does not exist", e
            );
        }
    }

    private void rejectBannedUser(AppUser user) {
        if (user.isBanned()) {
            throw new SecurityException("Access denied: Your account has been banned.");
        }
    }

    private UserDetails loadRefreshUserDetails(String username) {
        try {
            return userDetailsService.loadUserByUsername(username);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "User not found", e
            );
        }
    }
}
