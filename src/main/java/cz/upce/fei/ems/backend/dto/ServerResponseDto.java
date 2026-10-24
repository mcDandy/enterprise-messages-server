package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.MessageServer;
import cz.upce.fei.ems.backend.domain.MessageServerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@Schema(description = "Detail serveru")
public class ServerResponseDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "Hlavní kanál")
    private String name;

    @Schema(example = "2026-10-22T20:00:00Z")
    private OffsetDateTime createdAt;

    @Schema(example = "ORGANIZATION")
    private MessageServerType type;

    public static ServerResponseDto fromEntity(MessageServer server) {
        return ServerResponseDto.builder()
                .id(server.getId())
                .name(server.getName())
                .createdAt(server.getCreatedAt())
                .type(server.getType())
                .build();
    }
}