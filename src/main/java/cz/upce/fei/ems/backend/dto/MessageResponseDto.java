package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Detail zprávy")
public class MessageResponseDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "10")
    private Long channelId;

    @Schema(example = "5")
    private Long senderId;

    @Schema(description = "Šifrovaný obsah zprávy (E2EE)")
    private byte[] ciphertext;

    @Schema(example = "2026-10-22T20:00:00Z")
    private OffsetDateTime sentAt;

    @Schema(example = "2")
    private Long channelKeyId;

    @Schema(description = "Public UUID identifier of the encryption key generation")
    private UUID keyVersion;


    public static MessageResponseDto fromEntity(Message message) {
        return MessageResponseDto.builder()
                .id(message.getId())
                .channelId(message.getChannelId())
                .senderId(message.getSenderId())
                .ciphertext(message.getCiphertext())
                .sentAt(message.getSentAt())
                .channelKeyId(message.getChannelKeyId())
                .build();
    }
}