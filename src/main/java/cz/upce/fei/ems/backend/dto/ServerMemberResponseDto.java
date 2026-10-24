package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.domain.MessageServerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@Schema(description = "Detail člena serveru")
public class ServerMemberResponseDto {

    @Schema(example = "1")
    private Long serverId;

    @Schema(example = "5")
    private Long userId;

    @Schema(example = "MEMBER")
    private MessageServerRole role;

    @Schema(example = "2026-10-22T20:00:00Z")
    private OffsetDateTime joinedAt;

    public static ServerMemberResponseDto fromEntity(MessageServerMember member) {
        return ServerMemberResponseDto.builder()
                .serverId(member.getServerId())
                .userId(member.getUserId())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}