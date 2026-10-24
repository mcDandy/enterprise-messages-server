package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Informace o uživateli")
public class UserResponseDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "jan_novak")
    private String username;

    @Schema(example = "Honza Novák")
    private String displayName;

    @Schema(example = "2026-10-22T20:00:00Z")
    private OffsetDateTime createdAt;

    @Schema(example = "false")
    private boolean isBanned;

    @Schema(example = "false")
    private boolean isMainAdmin;
}