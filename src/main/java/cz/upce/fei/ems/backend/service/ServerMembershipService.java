package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.domain.MessageServerRole;
import cz.upce.fei.ems.backend.dto.ServerMemberResponseDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements server membership changes and role authorization.
 */

@Service
@RequiredArgsConstructor
public class ServerMembershipService {

    private final ServerMemberRepository serverMemberRepository;
    private final AppUserRepository appUserRepository;
    private final MessageServerRepository messageServerRepository;
    private final MessageServerService serverService;

    /** @return member representations for the server, or an empty list when there are no members */
    public List<ServerMemberResponseDto> getServerMembers(Long serverId) {
        return serverMemberRepository.findByServerId(serverId).stream()
                .map(ServerMemberResponseDto::fromEntity)
                .toList();
    }

    /**
     * Adds an existing account to a server after checking administrator authority.
     * A missing role defaults to {@link MessageServerRole#MEMBER}.
     *
     * @throws SecurityException when the actor is not an administrator
     * @throws IllegalArgumentException when the target account does not exist
     * @throws IllegalStateException when the target is already a member
     */
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

    /**
     * Removes an existing member after checking administrator authority; missing rows are a no-op.
     *
     * @throws SecurityException when the actor is not an administrator
     */
    @Transactional
    public void removeMemberFromServer(Long serverId, Long targetUserId, String adminUsername) {
        if (!messageServerRepository.existsById(serverId)) {
            return;
        }

        MessageServerMember member = serverMemberRepository.findByServerIdAndUserId(serverId, targetUserId)
                .orElse(null);
        if (member == null) {
            return;
        }

        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can remove members.");
        }

        serverMemberRepository.delete(member);
    }

    /**
     * Changes a member role after checking administrator authority.
     *
     * @throws SecurityException when the actor is not an administrator
     * @throws IllegalArgumentException when the target has no membership in the server
     */
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

    /** @return whether the named user has administrator membership in the given server */
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
