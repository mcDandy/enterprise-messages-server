package cz.upce.fei.ems.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Public encryption metadata added to the historic message response. */
public record MessageWithVersionDto(
        Long id,
        Long channelId,
        Long senderId,
        byte[] ciphertext,
        OffsetDateTime sentAt,
        Long channelKeyId,
        UUID keyVersion
) {}
