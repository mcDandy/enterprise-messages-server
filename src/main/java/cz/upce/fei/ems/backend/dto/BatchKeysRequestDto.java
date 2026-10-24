package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Požadavek na dávkové načtení klíčů")
public class BatchKeysRequestDto {

    @NotEmpty(message = "User IDs list must not be empty")
    @Schema(example = "[1, 2, 3]")
    private List<Long> userIds;
}