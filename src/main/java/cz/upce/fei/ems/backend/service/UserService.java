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

/**
 * Implements profile updates and user public-key management.
 */

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final UserKeyRepository userKeyRepository;

    /** Updates the profile display name for an existing account. */
    @Transactional
    public void updateDisplayName(String username, String displayName) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setDisplayName(displayName);
        appUserRepository.save(user);
    }

    /**
     * Revokes the existing active public key and stores the supplied replacement in one transaction.
     */
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

    /** @return the user’s active public key, or empty when none is currently registered */
    public Optional<UserKey> getActivePublicKeyForUser(Long userId) {
        return userKeyRepository.findByUserIdAndRevokedAtIsNull(userId);
    }

    /** Maps a present user to its public API representation. */
    public Optional<UserResponseDto> getUserResponse(AppUser user) {
        return Optional.ofNullable(user).map(UserResponseDto::fromEntity);
    }

    /** Maps the active public key to its API representation when one exists. */
    public Optional<UserKeyResponseDto> getActivePublicKeyResponse(Long userId) {
        return getActivePublicKeyForUser(userId).map(UserKeyResponseDto::fromEntity);
    }
}
