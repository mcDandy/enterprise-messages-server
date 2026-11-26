package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import cz.upce.fei.ems.backend.validation.constraints.NoControlCharacters;
import lombok.Data;

@Data
@Schema(description = "Požadavek na úpravu serveru")
public class UpdateServerRequestDto {

    @NotBlank(message = "Název serveru je povinný")
    @Size(max = 100, message = "Název serveru může mít maximálně 100 znaků")
    @Schema(example = "Nový název týmu")
    @NoControlCharacters
    private String name;
}
