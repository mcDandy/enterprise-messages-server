package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageServer;
import cz.upce.fei.ems.backend.domain.ServerMember;
import cz.upce.fei.ems.backend.domain.ServerType;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
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
@RequestMapping("/api/servers")
@RequiredArgsConstructor
@Tag(name = "Servers", description = "Server management and CRUD operations")
public class ServerController {

    private final ServerService serverService;
    private final MessageServerRepository serverRepository;
    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;

    @Operation(
            summary = "Get user servers",
            description = "Returns all servers the authenticated user is a member of."
    )
    @GetMapping
    public ResponseEntity<List<MessageServer>> getUserServers(Principal principal) {
        return ResponseEntity.ok(serverService.getServersForUser(principal.getName()));
    }

    @Operation(
            summary = "Create server",
            description = "Creates a new message server and assigns the creator as ADMIN."
    )
    @PostMapping
    public ResponseEntity<MessageServer> createServer(
            @RequestParam String name,
            @RequestParam ServerType type,
            Principal principal) {
        MessageServer server = serverService.createServer(name, type, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(server);
    }

    @Operation(summary = "Get server by ID", description = "Returns details of a specific server.")
    @GetMapping("/{id}")
    public ResponseEntity<MessageServer> getServer(@PathVariable Long id) {
        return serverRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update server", description = "Updates server details (Admin only).")
    @PutMapping("/{id}")
    public ResponseEntity<MessageServer> updateServer(
            @PathVariable Long id,
            @RequestBody MessageServer request,
            Principal principal) {
        if (!serverService.isUserAdmin(id, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return serverRepository.findById(id).map(server -> {
            server.setName(request.getName());
            return ResponseEntity.ok(serverRepository.save(server));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete server", description = "Deletes a server completely (Admin only).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id, Principal principal) {
        try {
            serverService.deleteServer(id, principal.getName());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Operation(
            summary = "Get servers of current user",
            description = "Returns all servers where the authenticated user is a member."
    )
    @GetMapping("/my")
    public ResponseEntity<List<MessageServer>> getMyServers(Principal principal) {
        AppUser user = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Long> serverIds = serverMemberRepository.findByUserId(user.getId())
                .stream()
                .map(ServerMember::getServerId)
                .toList();

        List<MessageServer> servers = serverRepository.findAllById(serverIds);
        return ResponseEntity.ok(servers);
    }
}