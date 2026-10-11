package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.service.ChannelMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers/{serverId}/channels/{channelId}/members")
@RequiredArgsConstructor
@Tag(name = "Channel Memberships", description = "Managing members inside specific channels")
public class ChannelMembershipController {

    private final ChannelMembershipService channelMembershipService;
    private final ChannelMemberRepository channelMemberRepository;

    @Operation(summary = "Get channel members", description = "Lists all members assigned to a specific channel.")
    @GetMapping
    public ResponseEntity<List<ChannelMember>> getChannelMembers(
            @PathVariable Long serverId,
            @PathVariable Long channelId) {
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        return ResponseEntity.ok(members);
    }

    @Operation(summary = "Add member to channel", description = "Adds a user to the channel (Server Admin only).")
    @PostMapping
    public ResponseEntity<Void> addMemberToChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId,
            @RequestBody Map<String, String> request,
            Principal principal) {
        String username = request.get("username");
        channelMembershipService.addMemberToChannel(serverId, channelId, username, principal.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Remove member from channel",
            description = "Removes a user from the channel (Server Admin only)."
    )
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMemberFromChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId,
            @PathVariable Long userId,
            Principal principal) {
        channelMembershipService.removeMemberFromChannel(serverId, channelId, userId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}