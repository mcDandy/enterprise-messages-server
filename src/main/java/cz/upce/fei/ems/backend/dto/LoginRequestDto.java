package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Login for token acquisition.")
public class LoginRequestDto {

    @Schema(
            example = "admin",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Username must not be empty.")
    private String username;

    @Schema(
            example = "admin",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password must not be empty.")
    private String password;
}