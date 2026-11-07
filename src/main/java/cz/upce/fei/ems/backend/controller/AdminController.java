package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.service.BanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final BanService banService;

    @PostMapping("/{id}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long id, @AuthenticationPrincipal AppUser admin) {
        banService.banUserGlobally(admin.getId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long id, @AuthenticationPrincipal AppUser admin) {
        banService.unbanUserGlobally(admin.getId(), id);
        return ResponseEntity.ok().build();
    }
}