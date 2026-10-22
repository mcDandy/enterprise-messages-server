package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Požadavek na úpravu kanálu")
public class UpdateChannelRequestDto {

    @NotBlank(message = "Název kanálu je povinný")
    @Size(max = 100, message = "Název kanálu může mít maximálně 100 znaků")
    @Schema(example = "announcements")
    private String name;
}