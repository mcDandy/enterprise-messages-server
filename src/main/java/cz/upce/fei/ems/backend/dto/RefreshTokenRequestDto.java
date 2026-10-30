package cz.upce.fei.ems.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequestDto {

    @NotBlank(message = "Token cannot be blank")
    private String refreshToken;
}