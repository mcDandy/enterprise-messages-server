package cz.upce.fei.ems.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshTokenRequestDto {

    @NotBlank(message = "Token cannot be blank")
    @Size(max = 4096, message = "Token is too long")
    private String refreshToken;
}
