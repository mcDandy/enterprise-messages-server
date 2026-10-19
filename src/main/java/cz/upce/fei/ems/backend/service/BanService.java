package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ServerMember;
import cz.upce.fei.ems.backend.domain.ServerRole;
import cz.upce.fei.ems.backend.net.session.SessionRegistry; // Import registru
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BanService {

    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final SessionRegistry sessionRegistry;

    @Transactional
    public void banUserGlobally(Long adminUserId, Long targetUserId) {
        AppUser admin = appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!admin.isMainAdmin()) {
            throw new SecurityException("Only main admin can ban users globally.");
        }

        if (admin.getId().equals(targetUserId)) {
            throw new IllegalStateException("You cannot ban yourself.");
        }

        AppUser targetUser = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        if (targetUser.isMainAdmin()) {
            throw new IllegalStateException("Cannot ban another main admin.");
        }

        targetUser.setBanned(true);
        appUserRepository.save(targetUser);

        sessionRegistry.removeAndCloseSession(targetUser.getUsername());
    }

    @Transactional
    public void unbanUserGlobally(Long adminUserId, Long targetUserId) {
        AppUser admin = appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!admin.isMainAdmin()) {
            throw new SecurityException("Only main admin can unban users globally.");
        }

        AppUser targetUser = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        targetUser.setBanned(false);
        appUserRepository.save(targetUser);
    }
}