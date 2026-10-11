package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import cz.upce.fei.ems.backend.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}/channels")
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Channel CRUD operations and management")
public class ChannelController {

    private final ChannelRepository channelRepository;
    private final ServerService serverService;
    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;

    @Operation(summary = "Get channels", description = "Lists all channels belonging to the server.")
    @GetMapping
    public ResponseEntity<List<Channel>> getChannels(@PathVariable Long serverId) {
        List<Channel> channels = channelRepository.findByServerIdOrderByNameAsc(serverId);
        return ResponseEntity.ok(channels);
    }

    @Operation(summary = "Get channel by ID", description = "Returns a specific channel detail.")
    @GetMapping("/{channelId}")
    public ResponseEntity<Channel> getChannel(@PathVariable Long serverId, @PathVariable Long channelId) {
        return channelRepository.findById(channelId)
                .filter(channel -> channel.getServerId().equals(serverId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create channel", description = "Creates a new channel in the server (Admin only).")
    @PostMapping
    public ResponseEntity<Channel> createChannel(
            @PathVariable Long serverId,
            @RequestBody Channel channelRequest,
            Principal principal) {

        if (!serverService.isUserAdmin(serverId, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (channelRepository.existsByServerIdAndName(serverId, channelRequest.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Channel channel = new Channel();
        channel.setServerId(serverId);
        channel.setName(channelRequest.getName());

        Channel savedChannel = channelRepository.save(channel);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedChannel);
    }

    @Operation(summary = "Update channel", description = "Updates channel name or details (Admin only).")
    @PutMapping("/{channelId}")
    public ResponseEntity<Channel> updateChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId,
            @RequestBody Channel channelRequest,
            Principal principal) {

        if (!serverService.isUserAdmin(serverId, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return channelRepository.findById(channelId)
                .filter(channel -> channel.getServerId().equals(serverId))
                .map(channel -> {
                    channel.setName(channelRequest.getName());
                    return ResponseEntity.ok(channelRepository.save(channel));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete channel", description = "Deletes a channel from the server (Admin only).")
    @DeleteMapping("/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId,
            Principal principal) {

        if (!serverService.isUserAdmin(serverId, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return channelRepository.findById(channelId)
                .filter(channel -> channel.getServerId().equals(serverId))
                .map(channel -> {
                    channelRepository.delete(channel);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get channels of server where user is member",
            description = "Returns channels in a server that the current user has access to."
    )
    @GetMapping("/my")
    public ResponseEntity<List<Channel>> getMyChannelsForServer(
            @PathVariable Long serverId,
            Principal principal) {

        AppUser user = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, user.getId())) {
            throw new SecurityException("You are not a member of this server.");
        }

        List<Channel> channels = channelRepository.findAccessibleChannelsByServerIdAndUserId(serverId, user.getId());

        return ResponseEntity.ok(channels);
    }
}