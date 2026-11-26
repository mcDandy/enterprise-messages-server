package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.MessageServerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Validated request body for changing a server member role.
 */

@Data
@Schema(description = "Požadavek na změnu role člena serveru")
public class UpdateMemberRoleRequestDto {

    @NotNull(message = "Role je povinná")
    @Schema(example = "ADMIN")
    private MessageServerRole role;
}
