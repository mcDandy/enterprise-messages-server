package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.dto.UserKeyResponseDto;
import cz.upce.fei.ems.backend.dto.UserResponseDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.UserKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final UserKeyRepository userKeyRepository;

    @Transactional
    public void updateDisplayName(String username, String displayName) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setDisplayName(displayName);
        appUserRepository.save(user);
    }

    @Transactional
    public void rotatePublicKey(String username, String newPublicKey) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        userKeyRepository.findByUserIdAndRevokedAtIsNull(user.getId()).ifPresent(oldKey -> {
            oldKey.setRevokedAt(OffsetDateTime.now());
            userKeyRepository.save(oldKey);
        });

        userKeyRepository.flush();

        UserKey key = new UserKey();
        key.setUserId(user.getId());
        key.setPublicKey(newPublicKey);
        userKeyRepository.save(key);
    }

    public Optional<UserKey> getActivePublicKeyForUser(Long userId) {
        return userKeyRepository.findByUserIdAndRevokedAtIsNull(userId);
    }

    public UserResponseDto mapToUserResponse(AppUser user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .isBanned(user.isBanned())
                .isMainAdmin(user.isMainAdmin())
                .build();
    }

    public UserKeyResponseDto mapToKeyResponse(UserKey userKey) {
        return UserKeyResponseDto.builder()
                .id(userKey.getId())
                .publicKey(userKey.getPublicKey())
                .build();
    }
}