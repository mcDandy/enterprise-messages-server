package cz.upce.fei.ems.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddChannelMemberRequestDto {
    @NotNull(message = "User ID is required")
    private Long userId;
}