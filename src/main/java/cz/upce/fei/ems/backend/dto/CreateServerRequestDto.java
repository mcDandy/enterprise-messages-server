package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.MessageServerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Požadavek na vytvoření nového serveru")
public class CreateServerRequestDto {

    @NotBlank(message = "Název serveru je povinný")
    @Size(max = 100, message = "Název serveru může mít maximálně 100 znaků")
    @Schema(example = "Vývojový tým")
    private String name;

    @NotNull(message = "Typ serveru je povinný")
    @Schema(example = "ORGANIZATION")
    private MessageServerType type;
}