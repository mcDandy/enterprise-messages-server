package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@Schema(description = "Detail komunikačního kanálu")
public class ChannelResponseDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "10")
    private Long serverId;

    @Schema(example = "general")
    private String name;

    @Schema(example = "2026-10-22T20:00:00Z")
    private OffsetDateTime createdAt;

    public static ChannelResponseDto fromEntity(Channel channel) {
        return ChannelResponseDto.builder()
                .id(channel.getId())
                .serverId(channel.getServerId())
                .name(channel.getName())
                .createdAt(channel.getCreatedAt())
                .build();
    }
}