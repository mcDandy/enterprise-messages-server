package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.validation.ValidRegistrationRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import cz.upce.fei.ems.backend.validation.constraints.MaxUtf8Bytes;
import cz.upce.fei.ems.backend.validation.constraints.NoControlCharacters;
import lombok.Data;

@Data
@ValidRegistrationRequest
public class RegisterRequestDto {

    @NotBlank(message = "Uživatelské jméno je povinné")
    @Size(max = 100, message = "Uživatelské jméno může mít maximálně 100 znaků")
    @NoControlCharacters
    private String username;

    @NotBlank(message = "Heslo je povinné")
    @MaxUtf8Bytes(value = 72, message = "Heslo může mít maximálně 72 bajtů v UTF-8")
    private String password;

    @NotBlank(message = "Veřejný klíč je povinný pro E2EE")
    @Size(max = 16_384, message = "Veřejný klíč je příliš dlouhý")
    private String publicKey;
}
