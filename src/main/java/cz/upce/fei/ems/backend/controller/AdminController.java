package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.service.BanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final BanService banService;
    private final AppUserRepository appUserRepository;

    @PostMapping("/{id}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long id, Principal principal) {
        AppUser admin = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        banService.banUserGlobally(admin.getId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long id, Principal principal) {
        AppUser admin = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        banService.unbanUserGlobally(admin.getId(), id);
        return ResponseEntity.ok().build();
    }
}