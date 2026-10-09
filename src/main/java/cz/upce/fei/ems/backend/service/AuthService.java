
package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.UserKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final UserKeyRepository userKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Uživatelské jméno je již zabrané"
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

        return AuthResponseDto.builder()
                .token(jwtService.generateToken(user.getUsername()))
                .username(user.getUsername())
                .build();
    }

    public AuthResponseDto authenticate(LoginRequestDto request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Uživatel neexistuje"
                ));

        if (!passwordEncoder.matches(
                request.getPassword(), user.getHashedPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Nesprávné heslo"
            );
        }

        return AuthResponseDto.builder()
                .token(jwtService.generateToken(user.getUsername()))
                .username(user.getUsername())
                .build();
    }

    public String getPublicKey(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Uživatel nenalezen"
                ));

        return userKeyRepository
                .findByUserIdAndRevokedAtIsNull(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Aktivní veřejný klíč nenalezen"
                ))
                .getPublicKey();
    }
}
