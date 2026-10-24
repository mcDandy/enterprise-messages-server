package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Rotace E2EE veřejného klíče")
public class RotatePublicKeyRequestDto {

    @NotBlank(message = "Public key must not be blank")
    @Schema(example = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")
    private String publicKey;
}