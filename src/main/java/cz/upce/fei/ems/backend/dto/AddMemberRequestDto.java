package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.MessageServerRole;
import jakarta.validation.constraints.NotNull;
import cz.upce.fei.ems.backend.validation.constraints.PositiveId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMemberRequestDto {
    @NotNull(message = "User ID is required")
    @PositiveId
    private Long userId;

    private MessageServerRole role;
}
