package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and key management")
public class UserController {

    private final UserService userService;
    private final AppUserRepository appUserRepository;

    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    public ResponseEntity<AppUser> getCurrentUser(Principal principal) {
        return appUserRepository.findByUsername(principal.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update display name")
    @PutMapping("/me/display-name")
    public ResponseEntity<Void> updateDisplayName(@RequestBody Map<String, String> request, Principal principal) {
        userService.updateDisplayName(principal.getName(), request.get("displayName"));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Rotate E2EE public key")
    @PostMapping("/me/keys")
    public ResponseEntity<Void> rotatePublicKey(@RequestBody Map<String, String> request, Principal principal) {
        userService.rotatePublicKey(principal.getName(), request.get("publicKey"));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get active public key of a specific user by ID")
    @GetMapping("/{userId}/key")
    public ResponseEntity<UserKey> getUserPublicKey(@PathVariable Long userId) {
        return userService.getActivePublicKeyForUser(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get active public keys for multiple users in batch")
    @PostMapping("/keys/batch")
    public ResponseEntity<Optional<UserKey>> getBatchPublicKeys(@RequestBody List<Long> userIds) {
        Optional<UserKey> keys = userService.getActivePublicKeysBatch(userIds);
        return ResponseEntity.ok(keys);
    }

}
