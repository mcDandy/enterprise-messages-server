package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponseDto {

    @Schema(
            description = "JWT token (Bearer)",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    )
    private String token;

    @Schema(
            example = "jan_novak"
    )
    private String username;

    @Schema(
            description = "Token expiration timestamp in milliseconds (Epoch)",
            example = "1717161600000"
    )
    private Long expiresAt;
}