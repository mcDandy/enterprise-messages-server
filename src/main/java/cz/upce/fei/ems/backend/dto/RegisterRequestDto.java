package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.validation.ValidRegistrationRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@ValidRegistrationRequest
public class RegisterRequestDto {

    @NotBlank(message = "Uživatelské jméno je povinné")
    private String username;

    @NotBlank(message = "Heslo je povinné")
    private String password;

    @NotBlank(message = "Veřejný klíč je povinný pro E2EE")
    private String publicKey;
}