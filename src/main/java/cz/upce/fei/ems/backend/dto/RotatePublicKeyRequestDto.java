package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Validated replacement public key submitted by the authenticated user.
 */

@Data
@Schema(description = "Rotace E2EE veřejného klíče")
public class RotatePublicKeyRequestDto {

    @NotBlank(message = "Public key must not be blank")
    @Size(max = 16_384, message = "Public key is too long")
    @Schema(example = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")
    private String publicKey;
}
