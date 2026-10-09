package cz.upce.fei.ems.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {

    @NotBlank(message = "Uživatelské jméno je povinné")
    @Size(min = 3, max = 50, message = "Uživatelské jméno musí mít 3 až 50 znaků")//todo, hmm to by možná taky chtělo do .envu
    private String username;

    @NotBlank(message = "Heslo je povinné")
    @Size(min = 6, message = "Heslo musí mít alespoň 6 znaků") //todo, taky
    private String password;

    @NotBlank(message = "Veřejný klíč je povinný pro E2EE")
    private String publicKey;
}