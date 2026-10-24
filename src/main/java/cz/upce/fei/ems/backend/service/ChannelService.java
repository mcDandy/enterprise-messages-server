package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final MessageServerService serverService;
    private final KeyRotationService keyRotationService;

    public List<Channel> getChannelsByServerId(Long serverId) {
        return channelRepository.findByServerIdOrderByNameAsc(serverId);
    }

    public Channel getChannelById(Long serverId, Long channelId) {
        return channelRepository.findById(channelId)
                .filter(channel -> channel.getServerId().equals(serverId))
                .orElse(null);
    }

    @Transactional
    public Channel createChannel(Long serverId, CreateChannelRequestDto request, String creatorUsername) {
        if (!serverService.isUserAdmin(serverId, creatorUsername)) {
            throw new SecurityException("Only server administrators can create channels.");
        }

        if (channelRepository.existsByServerIdAndName(serverId, request.getName())) {
            throw new IllegalStateException("Channel with name '" + request.getName() + "' already exists on this server.");
        }

        AppUser creator = appUserRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Creator user not found"));

        Channel channel = new Channel();
        channel.setServerId(serverId);
        channel.setName(request.getName());
        Channel savedChannel = channelRepository.save(channel);

        ChannelMember member = new ChannelMember();
        member.setChannelId(savedChannel.getId());
        member.setUserId(creator.getId());
        channelMemberRepository.save(member);

        keyRotationService.solicitateNewKeyRotation(savedChannel.getId(), Set.of(creatorUsername));

        return savedChannel;
    }

    @Transactional
    public Channel updateChannel(Long serverId, Long channelId, UpdateChannelRequestDto request, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can update channels.");
        }

        Channel channel = getChannelById(serverId, channelId);
        if (channel == null) {
            return null;
        }

        channel.setName(request.getName());
        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(Long serverId, Long channelId, String adminUsername) {
        if (!serverService.isUserAdmin(serverId, adminUsername)) {
            throw new SecurityException("Only server administrators can delete channels.");
        }

        Channel channel = getChannelById(serverId, channelId);
        if (channel != null) {
            channelRepository.delete(channel);
        }
    }

    public List<Channel> getMyChannelsForServer(Long serverId, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, user.getId())) {
            throw new SecurityException("You are not a member of this server.");
        }

        return channelRepository.findAccessibleChannelsByServerIdAndUserId(serverId, user.getId());
    }
}