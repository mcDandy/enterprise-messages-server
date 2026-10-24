package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageServer;
import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.ServerResponseDto;
import cz.upce.fei.ems.backend.dto.UpdateServerRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
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
@RequestMapping("/api/servers")
@RequiredArgsConstructor
@Tag(name = "Servers", description = "Server management and CRUD operations")
public class MessageServerController {

    private final MessageServerService serverService;
    private final MessageServerRepository serverRepository;
    private final AppUserRepository appUserRepository;
    private final ServerMemberRepository serverMemberRepository;

    @Operation(
            summary = "Get user servers",
            description = "Returns all servers the authenticated user is a member of.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of servers retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ServerResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ServerResponseDto>> getUserServers(Principal principal) {
        List<ServerResponseDto> servers = serverService.getServersForUser(principal.getName()).stream()
                .map(ServerResponseDto::fromEntity)
                .toList();
        return ResponseEntity.ok(servers);
    }

    @Operation(
            summary = "Create server",
            description = "Creates a new message server and assigns the creator as ADMIN.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Server created successfully",
                            content = @Content(schema = @Schema(implementation = ServerResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<ServerResponseDto> createServer(
            @Valid @RequestBody CreateServerRequestDto request,
            Principal principal) {
        MessageServer server = serverService.createServer(request.getName(), request.getType(), principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ServerResponseDto.fromEntity(server));
    }

    @Operation(
            summary = "Get server by ID",
            description = "Returns details of a specific server.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Server found",
                            content = @Content(schema = @Schema(implementation = ServerResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Server not found", content = @Content)
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<ServerResponseDto> getServer(@PathVariable Long id) {
        return serverRepository.findById(id)
                .map(ServerResponseDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Update server",
            description = "Updates server details (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Server updated successfully",
                            content = @Content(schema = @Schema(implementation = ServerResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Server not found", content = @Content)
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<ServerResponseDto> updateServer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServerRequestDto request,
            Principal principal) {
        if (!serverService.isUserAdmin(id, principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return serverRepository.findById(id).map(server -> {
            server.setName(request.getName());
            MessageServer saved = serverRepository.save(server);
            return ResponseEntity.ok(ServerResponseDto.fromEntity(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete server",
            description = "Deletes a server completely (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Server deleted successfully"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Server not found", content = @Content)
            }
    )
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
            description = "Returns all servers where the authenticated user is a member.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of servers retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ServerResponseDto.class)))
            }
    )
    @GetMapping("/my")
    public ResponseEntity<List<ServerResponseDto>> getMyServers(Principal principal) {
        AppUser user = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Long> serverIds = serverMemberRepository.findByUserId(user.getId())
                .stream()
                .map(MessageServerMember::getServerId)
                .toList();

        List<ServerResponseDto> servers = serverRepository.findAllById(serverIds).stream()
                .map(ServerResponseDto::fromEntity)
                .toList();
        return ResponseEntity.ok(servers);
    }
}