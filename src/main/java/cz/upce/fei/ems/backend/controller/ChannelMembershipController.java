package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.AddChannelMemberRequestDto;
import cz.upce.fei.ems.backend.dto.ChannelMemberResponseDto;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.service.ChannelMembershipService;
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

@RestController
@RequestMapping("/api/channels/{channelId}/members")
@RequiredArgsConstructor
@Tag(name = "Channel Memberships", description = "Managing members inside specific channels")
public class ChannelMembershipController {

    private final ChannelMembershipService channelMembershipService;
    private final ChannelMemberRepository channelMemberRepository;

    @Operation(
            summary = "Get channel members",
            description = "Lists all members assigned to a specific channel.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of channel members retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChannelMemberResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ChannelMemberResponseDto>> getChannelMembers(@PathVariable Long channelId) {
        List<ChannelMemberResponseDto> members = channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMemberResponseDto::fromEntity)
                .toList();
        return ResponseEntity.ok(members);
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
    public ResponseEntity<Void> addMemberToChannel(
            @PathVariable Long channelId,
            @Valid @RequestBody AddChannelMemberRequestDto request,
            Principal principal) {
        channelMembershipService.addMemberToChannel(channelId, request.getUserId(), principal.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Remove member from channel",
            description = "Removes a user from the channel (Server Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Member removed from channel successfully"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Member not found", content = @Content)
            }
    )
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMemberFromChannel(
            @PathVariable Long channelId,
            @PathVariable Long userId,
            Principal principal) {
        channelMembershipService.removeMemberFromChannel(channelId, userId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}