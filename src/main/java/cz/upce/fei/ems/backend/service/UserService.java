package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.UserKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
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

        // Revoke current active public key
        userKeyRepository.findByUserIdAndRevokedAtIsNull(user.getId()).ifPresent(oldKey -> {
            oldKey.setRevokedAt(OffsetDateTime.now());
            userKeyRepository.save(oldKey);
        });

        // Register new active public key
        UserKey key = new UserKey();
        key.setUserId(user.getId());
        key.setPublicKey(newPublicKey);
        userKeyRepository.save(key);
    }

    public Optional<UserKey> getActivePublicKeyForUser(Long userId) {
        return userKeyRepository.findByUserIdAndRevokedAtIsNull(userId);
    }

    public Optional<UserKey> getActivePublicKeysBatch(List<Long> userIds) {
        return userKeyRepository.findByUserIdInAndRevokedAtIsNull(userIds);
    }
}