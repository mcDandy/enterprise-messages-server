package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.dto.ChannelResponseDto;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.UpdateChannelRequestDto;
import cz.upce.fei.ems.backend.service.ChannelService;
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
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Channel CRUD operations and management")
public class ChannelController {

    private final ChannelService channelService;

    @Operation(
            summary = "Get channels by server",
            description = "Lists all channels belonging to the server.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of channels retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class)))
            }
    )
    @GetMapping("/api/servers/{serverId}/channels")
    public ResponseEntity<List<ChannelResponseDto>> getChannels(@PathVariable Long serverId) {
        List<ChannelResponseDto> channels = channelService.getChannelsByServerId(serverId).stream()
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
    @GetMapping("/api/channels/{channelId}")
    public ResponseEntity<ChannelResponseDto> getChannel(@PathVariable Long channelId) {
        Channel channel = channelService.getChannelById(channelId);
        if (channel == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ChannelResponseDto.fromEntity(channel));
    }

    @Operation(
            summary = "Create channel",
            description = "Creates a new channel in the server and adds the creator as a channel member (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Channel created successfully",
                            content = @Content(schema = @Schema(implementation = ChannelResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Channel name already exists", content = @Content)
            }
    )
    @PostMapping("/api/servers/{serverId}/channels")
    public ResponseEntity<ChannelResponseDto> createChannel(
            @PathVariable Long serverId,
            @Valid @RequestBody CreateChannelRequestDto request,
            Principal principal) {

        try {
            Channel savedChannel = channelService.createChannel(serverId, request, principal.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponseDto.fromEntity(savedChannel));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
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
    @PutMapping("/api/channels/{channelId}")
    public ResponseEntity<ChannelResponseDto> updateChannel(
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelRequestDto request,
            Principal principal) {

        try {
            Channel updatedChannel = channelService.updateChannel(channelId, request, principal.getName());
            if (updatedChannel == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ChannelResponseDto.fromEntity(updatedChannel));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Operation(
            summary = "Delete channel",
            description = "Deletes a channel (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Channel deleted successfully"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Channel not found", content = @Content)
            }
    )
    @DeleteMapping("/api/channels/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            @PathVariable Long channelId,
            Principal principal) {

        try {
            channelService.deleteChannel(channelId, principal.getName());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
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
    @GetMapping("/api/servers/{serverId}/channels/my")
    public ResponseEntity<List<ChannelResponseDto>> getMyChannelsForServer(
            @PathVariable Long serverId,
            Principal principal) {

        try {
            List<ChannelResponseDto> channels = channelService.getMyChannelsForServer(serverId, principal.getName())
                    .stream()
                    .map(ChannelResponseDto::fromEntity)
                    .toList();
            return ResponseEntity.ok(channels);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}