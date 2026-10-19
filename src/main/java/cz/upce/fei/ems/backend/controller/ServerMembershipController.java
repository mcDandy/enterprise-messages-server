package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.ServerMember;
import cz.upce.fei.ems.backend.domain.ServerRole;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import cz.upce.fei.ems.backend.service.ServerMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers/{serverId}/members")
@RequiredArgsConstructor
@Tag(name = "Server Memberships", description = "Managing members and roles within servers")
public class ServerMembershipController {

    private final ServerMembershipService membershipService;
    private final ServerMemberRepository serverMemberRepository;
    private final ServerMembershipService serverMembershipService;

    @Operation(summary = "List server members")
    @GetMapping
    public ResponseEntity<List<ServerMember>> getMembers(@PathVariable Long serverId) {
        return ResponseEntity.ok(serverMemberRepository.findByServerId(serverId));
    }

    @Operation(summary = "Add member to server (Admin only)")
    @PostMapping
    public ResponseEntity<Void> addMember(
            @PathVariable Long serverId,
            @RequestBody Map<String, String> request,
            Principal principal) {
        String username = request.get("username");
        ServerRole role = request.containsKey("role") ? ServerRole.valueOf(request.get("role")) : ServerRole.MEMBER;

        membershipService.addMemberToServer(serverId, username, role, principal.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove member from server (Admin only)")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long serverId,
            @PathVariable Long userId,
            Principal principal) {
        membershipService.removeMemberFromServer(serverId, userId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update member role", description = "Changes role of a server member (Server Admin only).")
    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable Long serverId,
            @PathVariable Long userId,
            @RequestBody Map<String, String> request,
            Principal principal) {

        ServerRole newRole = ServerRole.valueOf(request.get("role"));
        serverMembershipService.updateMemberRole(serverId, userId, newRole, principal.getName());
        return ResponseEntity.ok().build();
    }
}