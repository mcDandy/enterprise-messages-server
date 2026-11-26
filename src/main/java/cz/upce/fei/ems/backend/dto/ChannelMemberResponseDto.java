package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * API representation of a channel member and that members message progress.
 */

@Data
@Builder
@Schema(description = "Detail člena kanálu")
public class ChannelMemberResponseDto {

    @Schema(example = "10")
    private Long channelId;

    @Schema(example = "5")
    private Long userId;

    @Schema(example = "2026-10-22T20:00:00Z")
    private OffsetDateTime joinedAt;

    @Schema(example = "123")
    private Long lastDeliveredMessageId;

    @Schema(example = "120")
    private Long lastReadMessageId;

    public static ChannelMemberResponseDto fromEntity(ChannelMember member) {
        return ChannelMemberResponseDto.builder()
                .channelId(member.getChannelId())
                .userId(member.getUserId())
                .joinedAt(member.getJoinedAt())
                .lastDeliveredMessageId(member.getLastDeliveredMessageId())
                .lastReadMessageId(member.getLastReadMessageId())
                .build();
    }
}
