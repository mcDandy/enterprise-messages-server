package cz.upce.fei.ems.backend.dto;

import jakarta.validation.constraints.NotNull;
import cz.upce.fei.ems.backend.validation.constraints.PositiveId;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for adding an existing server member to a channel.
 */

@Getter
@Setter
public class AddChannelMemberRequestDto {
    @NotNull(message = "User ID is required")
    @PositiveId
    private Long userId;
}
