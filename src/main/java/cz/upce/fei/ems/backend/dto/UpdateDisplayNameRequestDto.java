package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Změna zobrazovaného jména")
public class UpdateDisplayNameRequestDto {

    @NotBlank(message = "Display name must not be blank")
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    @Schema(example = "Honza Novák")
    private String displayName;
}