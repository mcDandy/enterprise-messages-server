package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.dto.ActionResponseDto;
import cz.upce.fei.ems.backend.service.BanService;
import cz.upce.fei.ems.backend.validation.constraints.PositiveId;
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
    public ResponseEntity<ActionResponseDto> banUser(@PathVariable @PositiveId Long id, @AuthenticationPrincipal AppUser admin) {
        banService.banUserGlobally(admin.getId(), id);
        return ResponseEntity.ok(ActionResponseDto.completed());
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<ActionResponseDto> unbanUser(@PathVariable @PositiveId Long id, @AuthenticationPrincipal AppUser admin) {
        banService.unbanUserGlobally(admin.getId(), id);
        return ResponseEntity.ok(ActionResponseDto.completed());
    }
}
