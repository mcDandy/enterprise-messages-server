package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ServerService serverService;

    @Transactional
    public void addMemberToChannel(Long serverId, Long channelId, String targetUsername, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }

        AppUser targetUser = appUserRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, targetUser.getId())) {
            throw new IllegalStateException("User is already a member of this channel.");
        }

        ChannelMember member = new ChannelMember();
        member.setChannelId(channelId);
        member.setUserId(targetUser.getId());

        channelMemberRepository.save(member);
    }

    @Transactional
    public void removeMemberFromChannel(Long serverId, Long channelId, Long targetUserId, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can manage channel memberships.");
        }

        channelMemberRepository.findById(new cz.upce.fei.ems.backend.domain.ChannelMemberId(channelId, targetUserId))
                .ifPresent(channelMemberRepository::delete);
    }
}