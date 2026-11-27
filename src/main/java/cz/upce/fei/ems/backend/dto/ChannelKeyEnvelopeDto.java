package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.validation.constraints.PositiveId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** An encrypted channel-key envelope addressed to one channel member. */
@Getter
@Setter
@Schema(description = "Encrypted channel key for one recipient")
public class ChannelKeyEnvelopeDto {
    @NotNull
    @PositiveId
    @Schema(example = "12")
    private Long userId;

    @NotBlank
    @Size(max = 1_398_104)
    @Pattern(regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    @Schema(description = "Standard Base64 ciphertext; plaintext channel keys must never be sent")
    private String ciphertext;
}
