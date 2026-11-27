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

/**
 * Implements channel lookup, lifecycle, and channel-related authorization.
 */

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

    /**
     * Finds channels in a server, preserving alphabetical order.
     *
     * @return empty when the parent server does not exist; otherwise a possibly empty list
     */
    public Optional<List<ChannelResponseDto>> getChannelsByServerId(Long serverId) {
        if (!messageServerRepository.existsById(serverId)) {
            return Optional.empty();
        }
        return Optional.of(channelRepository.findByServerIdOrderByNameAsc(serverId).stream()
                .map(ChannelResponseDto::fromEntity)
                .toList());
    }

    /** @return the channel representation, or empty when no channel has the requested ID */
    public Optional<ChannelResponseDto> getChannelById(Long channelId) {
        return channelRepository.findById(channelId).map(ChannelResponseDto::fromEntity);
    }

    /**
     * Creates a channel for a server administrator, adds the creator as its first member, and starts key rotation.
     *
     * @param serverId parent server
     * @param request validated channel properties
     * @param creatorUsername authenticated creator
     * @return representation of the persisted channel
     * @throws SecurityException when the user is not a server administrator
     * @throws IllegalStateException when the server already has a channel with the requested name
     */
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

    /**
     * Changes a channel name after checking server-admin authority.
     *
     * @return updated representation, or empty when the channel does not exist
     * @throws SecurityException when the user is not a server administrator
     */
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

    /**
     * Deletes a channel and its dependent records; deleting an absent channel is a no-op.
     *
     * @throws SecurityException when the user is not a server administrator
     */
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

    /**
     * Lists active public keys for channel members other than the requesting user.
     * Members without a current key are omitted.
     */
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

    /**
     * Lists only channels visible to the user in the given server.
     *
     * @throws SecurityException when the user is not a member of the server
     */
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
