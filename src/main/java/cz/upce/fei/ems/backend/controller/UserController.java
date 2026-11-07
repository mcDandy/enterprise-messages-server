package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.dto.*;
import cz.upce.fei.ems.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and key management")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Get current user profile",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile found", content = @Content(
                            schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
            }
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(@AuthenticationPrincipal AppUser user) {
        return userService.getUserResponse(user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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
            @Valid @RequestBody UpdateDisplayNameRequestDto request, @AuthenticationPrincipal AppUser user) {
        userService.updateDisplayName(user.getUsername(), request.getDisplayName());
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
            @Valid @RequestBody RotatePublicKeyRequestDto request, @AuthenticationPrincipal AppUser user
    ) {
        userService.rotatePublicKey(user.getUsername(), request.getPublicKey());
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
        return userService.getActivePublicKeyResponse(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}