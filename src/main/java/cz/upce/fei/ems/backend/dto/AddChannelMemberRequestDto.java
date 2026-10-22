package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Požadavek na přidání uživatele do kanálu")
public class AddChannelMemberRequestDto {

    @NotBlank(message = "Uživatelské jméno je povinné")
    @Schema(example = "jan_novak")
    private String username;
}