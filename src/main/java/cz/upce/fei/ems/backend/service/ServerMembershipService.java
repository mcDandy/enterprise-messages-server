package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ServerMember;
import cz.upce.fei.ems.backend.domain.ServerRole;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServerMembershipService {

    private final ServerMemberRepository serverMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ServerService serverService;

    @Transactional
    public void addMemberToServer(Long serverId, String targetUsername, ServerRole role, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can add members.");
        }

        AppUser targetUser = appUserRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        if (serverMemberRepository.findByServerIdAndUserId(serverId, targetUser.getId()).isPresent()) {
            throw new IllegalStateException("User is already a member of this server.");
        }

        ServerMember member = new ServerMember();
        member.setServerId(serverId);
        member.setUserId(targetUser.getId());
        member.setRole(role != null ? role : ServerRole.MEMBER);

        serverMemberRepository.save(member);
    }

    @Transactional
    public void removeMemberFromServer(Long serverId, Long targetUserId, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can remove members.");
        }

        serverMemberRepository.findByServerIdAndUserId(serverId, targetUserId)
                .ifPresent(serverMemberRepository::delete);
    }

    @Transactional
    public void updateMemberRole(Long serverId, Long targetUserId, ServerRole newRole, String adminUsername) {
        if (!isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can change member roles.");
        }

        ServerMember member = serverMemberRepository.findByServerIdAndUserId(serverId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this server."));

        member.setRole(newRole);
        serverMemberRepository.save(member);
    }

    public boolean isUserAdmin(Long serverId, String username) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }

        return serverMemberRepository.findByServerIdAndUserId(serverId, user.getId())
                .map(member -> member.getRole() == ServerRole.ADMINISTRATOR)
                .orElse(false);
    }
}