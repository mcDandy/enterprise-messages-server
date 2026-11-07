package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.dto.ChannelResponseDto;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.UserKeyResponseDto;
import cz.upce.fei.ems.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final MessageServerService serverService;
    private final ChannelDeletionService channelDeletionService;
    private final KeyRotationService keyRotationService;
    private final UserKeyRepository userKeyRepository;
    private final MessageServerRepository messageServerRepository;

    public Optional<List<ChannelResponseDto>> getChannelsByServerId(Long serverId) {
        if (!messageServerRepository.existsById(serverId)) {
            return Optional.empty();
        }
        return Optional.of(channelRepository.findByServerIdOrderByNameAsc(serverId).stream()
                .map(ChannelResponseDto::fromEntity)
                .toList());
    }

    public Optional<ChannelResponseDto> getChannelById(Long channelId) {
        return channelRepository.findById(channelId).map(ChannelResponseDto::fromEntity);
    }

    @Transactional
    public ChannelResponseDto createChannel(Long serverId, CreateChannelRequestDto request, String creatorUsername) {
        if (!serverService.isUserAdmin(serverId, creatorUsername)) {
            throw new SecurityException("Only server administrators can create channels.");
        }

        if (channelRepository.existsByServerIdAndName(serverId, request.getName())) {
            throw new IllegalStateException(
                    "Channel with name '" + request.getName() + "' already exists on this server."
            );
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

        return ChannelResponseDto.fromEntity(savedChannel);
    }

    @Transactional
    public Optional<ChannelResponseDto> updateChannel(Long channelId, UpdateChannelRequestDto request, String adminUsername) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return Optional.empty();
        }

        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can update channels.");
        }

        channel.setName(request.getName());
        return Optional.of(ChannelResponseDto.fromEntity(channelRepository.save(channel)));
    }

    @Transactional
    public void deleteChannel(Long channelId, String adminUsername) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return;
        }

        if (!serverService.isUserAdmin(channel.getServerId(), adminUsername)) {
            throw new SecurityException("Only server administrators can delete channels.");
        }

        channelDeletionService.deleteChannel(channel);
    }

    public List<UserKeyResponseDto> getActivePublicKeysForChannel(Long channelId, String username) {
        List<Long> userIds = new java.util.ArrayList<>(channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMember::getUserId)
                .toList());
        appUserRepository.findByUsername(username).ifPresent(user -> userIds.remove(user.getId()));

        return userIds.stream()
                .map(userId -> userKeyRepository.findByUserIdAndRevokedAtIsNull(userId).orElse(null))
                .filter(Objects::nonNull)
                .map(UserKeyResponseDto::fromEntity)
                .toList();
    }

    public List<ChannelResponseDto> getMyChannelsForServer(Long serverId, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, user.getId())) {
            throw new SecurityException("You are not a member of this server.");
        }

        return channelRepository.findAccessibleChannelsByServerIdAndUserId(serverId, user.getId()).stream()
                .map(ChannelResponseDto::fromEntity)
                .toList();
    }
}