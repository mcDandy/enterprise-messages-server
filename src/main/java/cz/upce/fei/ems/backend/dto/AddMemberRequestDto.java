package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.MessageServerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Požadavek na přidání člena na server")
public class AddMemberRequestDto {

    @NotBlank(message = "Uživatelské jméno je povinné")
    @Schema(example = "jan_novak")
    private String username;

    @Schema(example = "MEMBER", defaultValue = "MEMBER")
    private MessageServerRole role;
}