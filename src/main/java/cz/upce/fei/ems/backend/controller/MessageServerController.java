package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.ServerResponseDto;
import cz.upce.fei.ems.backend.dto.UpdateServerRequestDto;
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
        return ResponseEntity.ok(serverService.getServersForUser(principal.getName()));
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serverService.createServer(request, principal.getName()));
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
        return serverService.getServer(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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
        return serverService.updateServer(id, request, principal.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete server",
            description = "Deletes a server completely (Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204",
                            description = "Deletion completed; succeeds if the server was already absent"),
                    @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
            }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id, Principal principal) {
        serverService.deleteServer(id, principal.getName());
        return ResponseEntity.noContent().build();
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
        return ResponseEntity.ok(serverService.getMyServers(principal.getName()));
    }
}