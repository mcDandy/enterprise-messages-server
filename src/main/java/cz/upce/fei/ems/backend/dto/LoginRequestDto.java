package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import cz.upce.fei.ems.backend.validation.constraints.MaxUtf8Bytes;
import cz.upce.fei.ems.backend.validation.constraints.NoControlCharacters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Validated credentials submitted to the login endpoint.
 */

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
    @Size(max = 100, message = "Username must not exceed 100 characters.")
    @NoControlCharacters
    private String username;

    @Schema(
            example = "admin",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password must not be empty.")
    @MaxUtf8Bytes(value = 72, message = "Password must not exceed 72 UTF-8 bytes.")
    private String password;
}
