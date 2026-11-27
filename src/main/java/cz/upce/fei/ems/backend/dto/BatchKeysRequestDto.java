package cz.upce.fei.ems.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body containing channel-key updates for a batch of recipients.
 */

@Data
@Schema(description = "Požadavek na dávkové načtení klíčů")
public class BatchKeysRequestDto {

    @NotEmpty(message = "User IDs list must not be empty")
    @Size(max = 100, message = "At most 100 user IDs may be requested")
    @Schema(example = "[1, 2, 3]")
    private List<@NotNull @Positive Long> userIds;
}
