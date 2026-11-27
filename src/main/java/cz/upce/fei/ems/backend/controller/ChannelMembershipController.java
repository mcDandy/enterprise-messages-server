package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.AddChannelMemberRequestDto;
import cz.upce.fei.ems.backend.dto.ActionResponseDto;
import cz.upce.fei.ems.backend.dto.ChannelMemberResponseDto;
import cz.upce.fei.ems.backend.service.ChannelMembershipService;
import cz.upce.fei.ems.backend.validation.constraints.PositiveId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Exposes operations for listing and managing channel membership.
 */

@RestController
@RequestMapping("/api/channels/{channelId}/members")
@RequiredArgsConstructor
@Tag(name = "Channel Memberships", description = "Managing members inside specific channels")
public class ChannelMembershipController {

    private final ChannelMembershipService channelMembershipService;

    @Operation(
            summary = "Get channel members",
            description = "Lists all members assigned to a specific channel.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of channel members retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChannelMemberResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ChannelMemberResponseDto>> getChannelMembers(
            @PathVariable @PositiveId Long channelId) {
        return ResponseEntity.ok(channelMembershipService.getChannelMembers(channelId));
    }

    @Operation(
            summary = "Add member to channel",
            description = "Adds a user to the channel (Server Admin only).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Member added to channel successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Channel or user not found", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<ActionResponseDto> addMemberToChannel(
            @PathVariable @PositiveId Long channelId,
            @Valid @RequestBody AddChannelMemberRequestDto request,
            Principal principal) {
        channelMembershipService.addMemberAndTriggerKeyRotation(channelId, request.getUserId(), principal.getName());
        return ResponseEntity.ok(ActionResponseDto.completed());
    }

    @Operation(
            summary = "Remove member from channel",
            description = "Removes a user from the channel (Server Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204",
                            description = "Removal completed; succeeds if the membership was already absent"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
            }
    )
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMemberFromChannel(
            @PathVariable @PositiveId Long channelId,
            @PathVariable @PositiveId Long userId,
            Principal principal) {
        channelMembershipService.removeMemberFromChannelAndTriggerKeyRotation(channelId, userId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
