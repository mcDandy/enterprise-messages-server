package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.dto.*;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.UserService;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and key management")
public class UserController {

    private final UserService userService;
    private final AppUserRepository appUserRepository;

    @Operation(
            summary = "Get current user profile",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile found", content = @Content(
                            schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
            }
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(Principal principal) {
        return appUserRepository.findByUsername(principal.getName())
                .map(user -> ResponseEntity.ok(userService.mapToUserResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Update display name",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Display name updated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content)
            }
    )
    @PutMapping("/me/display-name")
    public ResponseEntity<Void> updateDisplayName(
            @Valid @RequestBody UpdateDisplayNameRequestDto request, Principal principal) {
        userService.updateDisplayName(principal.getName(), request.getDisplayName());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Rotate E2EE public key",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Public key rotated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content)
            }
    )
    @PostMapping("/me/keys")
    public ResponseEntity<Void> rotatePublicKey(
            @Valid @RequestBody RotatePublicKeyRequestDto request, Principal principal
    ) {
        userService.rotatePublicKey(principal.getName(), request.getPublicKey());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get active public key of a specific user by ID",
            responses = {
                    @ApiResponse(
                            responseCode = "200", description = "Key found", content = @Content(
                            schema = @Schema(implementation = UserKeyResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Key not found", content = @Content)
            }
    )
    @GetMapping("/{userId}/key")
    public ResponseEntity<UserKeyResponseDto> getUserPublicKey(@PathVariable Long userId) {
        return userService.getActivePublicKeyForUser(userId)
                .map(key -> ResponseEntity.ok(userService.mapToKeyResponse(key)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get active public keys for multiple users in batch",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Keys retrieved successfully",
                            content = @Content(schema = @Schema(implementation = UserKeyResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Key not found", content = @Content)
            }
    )
    @PostMapping("/keys/batch")
    public ResponseEntity<UserKeyResponseDto> getBatchPublicKeys(@Valid @RequestBody BatchKeysRequestDto request) {
        return userService.getActivePublicKeysBatch(request.getUserIds())
                .map(userService::mapToKeyResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}