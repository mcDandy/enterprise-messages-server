package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.domain.MessageServerRole;
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
    private final MessageServerService serverService;

    @Transactional
    public void addMemberToServer(Long serverId, Long targetUserId, MessageServerRole role, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can add members.");
        }

        if (!appUserRepository.existsById(targetUserId)) {
            throw new IllegalArgumentException("Target user with ID " + targetUserId + " not found");
        }

        if (serverMemberRepository.findByServerIdAndUserId(serverId, targetUserId).isPresent()) {
            throw new IllegalStateException("User is already a member of this server.");
        }

        MessageServerMember member = new MessageServerMember();
        member.setServerId(serverId);
        member.setUserId(targetUserId);
        member.setRole(role != null ? role : MessageServerRole.MEMBER);

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
    public void updateMemberRole(Long serverId, Long targetUserId, MessageServerRole newRole, String adminUsername) {
        if (!isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can change member roles.");
        }

        MessageServerMember member = serverMemberRepository.findByServerIdAndUserId(serverId, targetUserId)
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
                .map(member -> member.getRole() == MessageServerRole.ADMINISTRATOR)
                .orElse(false);
    }
}