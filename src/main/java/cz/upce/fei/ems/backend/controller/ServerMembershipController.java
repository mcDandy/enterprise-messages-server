package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.MessageServerRole;
import cz.upce.fei.ems.backend.dto.AddMemberRequestDto;
import cz.upce.fei.ems.backend.dto.ServerMemberResponseDto;
import cz.upce.fei.ems.backend.dto.UpdateMemberRoleRequestDto;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import cz.upce.fei.ems.backend.service.ServerMembershipService;
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
@RequestMapping("/api/servers/{serverId}/members")
@RequiredArgsConstructor
@Tag(name = "Server Memberships", description = "Managing members and roles within servers")
public class ServerMembershipController {

    private final ServerMembershipService membershipService;
    private final ServerMemberRepository serverMemberRepository;

    @Operation(
            summary = "List server members",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of members retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ServerMemberResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ServerMemberResponseDto>> getMembers(@PathVariable Long serverId) {
        List<ServerMemberResponseDto> members = serverMemberRepository.findByServerId(serverId).stream()
                .map(ServerMemberResponseDto::fromEntity)
                .toList();
        return ResponseEntity.ok(members);
    }

    @Operation(
            summary = "Add member to server (Admin only)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Member added successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<Void> addMember(
            @PathVariable Long serverId,
            @Valid @RequestBody AddMemberRequestDto request,
            Principal principal) {

        MessageServerRole role = request.getRole() != null ? request.getRole() : MessageServerRole.MEMBER;
        membershipService.addMemberToServer(serverId, request.getUsername(), role, principal.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Remove member from server (Admin only)",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Member removed successfully"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Member not found", content = @Content)
            }
    )
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long serverId,
            @PathVariable Long userId,
            Principal principal) {
        membershipService.removeMemberFromServer(serverId, userId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Update member role",
            description = "Changes role of a server member (Server Admin only).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Role updated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
            }
    )
    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable Long serverId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequestDto request,
            Principal principal) {

        membershipService.updateMemberRole(serverId, userId, request.getRole(), principal.getName());
        return ResponseEntity.ok().build();
    }
}