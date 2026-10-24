package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.dto.ChannelResponseDto;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import cz.upce.fei.ems.backend.service.MessageServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final MessageServerService serverService;
    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;

    @Operation(
            summary = "Get channels",
            description = "Lists all channels belonging to the server.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of channels retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ChannelResponseDto>> getChannels(@PathVariable Long serverId) {
        List<ChannelResponseDto> channels = channelRepository.findByServerIdOrderByNameAsc(serverId).stream()
                .map(ChannelResponseDto::fromEntity)
                .toList();
        return ResponseEntity.ok(channels);
    }

    @Operation(
            summary = "Get channel by ID",
            description = "Returns a specific channel detail.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Channel found",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Channel not found", content = @Content)
            }
    )
    @GetMapping("/{channelId}")
    public ResponseEntity<ChannelResponseDto> getChannel(@PathVariable Long serverId, @PathVariable Long channelId) {
        return channelRepository.findById(channelId)
                .filter(channel -> channel.getServerId().equals(serverId))
                .map(ChannelResponseDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create channel",
            description = "Creates a new channel in the server (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Channel created successfully",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Channel name already exists", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<ChannelResponseDto> createChannel(
            @PathVariable Long serverId,
            @Valid @RequestBody CreateChannelRequestDto request,
            Principal principal) {

        if (!serverService.isUserAdmin(serverId, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (channelRepository.existsByServerIdAndName(serverId, request.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Channel channel = new Channel();
        channel.setServerId(serverId);
        channel.setName(request.getName());

        Channel savedChannel = channelRepository.save(channel);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponseDto.fromEntity(savedChannel));
    }

    @Operation(
            summary = "Update channel",
            description = "Updates channel name or details (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Channel updated successfully",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Channel not found", content = @Content)
            }
    )
    @PutMapping("/{channelId}")
    public ResponseEntity<ChannelResponseDto> updateChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelRequestDto request,
            Principal principal) {

        if (!serverService.isUserAdmin(serverId, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return channelRepository.findById(channelId)
                .filter(channel -> channel.getServerId().equals(serverId))
                .map(channel -> {
                    channel.setName(request.getName());
                    Channel saved = channelRepository.save(channel);
                    return ResponseEntity.ok(ChannelResponseDto.fromEntity(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete channel",
            description = "Deletes a channel from the server (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Channel deleted successfully"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Channel not found", content = @Content)
            }
    )
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
            description = "Returns channels in a server that the current user has access to.",
            responses = {
                    @ApiResponse(
                            responseCode = "200", description = "List of accessible channels retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class)))
            }
    )
    @GetMapping("/my")
    public ResponseEntity<List<ChannelResponseDto>> getMyChannelsForServer(
            @PathVariable Long serverId,
            Principal principal) {

        AppUser user = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, user.getId())) {
            throw new SecurityException("You are not a member of this server.");
        }

        List<ChannelResponseDto> channels
                = channelRepository.findAccessibleChannelsByServerIdAndUserId(serverId, user.getId())
                .stream()
                .map(ChannelResponseDto::fromEntity)
                .toList();

        return ResponseEntity.ok(channels);
    }
}