package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final cz.upce.fei.ems.backend.service.JwtService jwtService;

    public AuthResponseDto authenticate(LoginRequestDto request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User doesn't exist"));


        if (!passwordEncoder.matches(request.getPassword(), user.getHashedPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong password");
        }

        return AuthResponseDto.builder()
                .token(jwtService.generateToken(user.getUsername()))
                .username(user.getUsername())
                .build();
    }
}
