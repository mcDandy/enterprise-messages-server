package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Database queries for channel memberships and their progress fields.
 */

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, ChannelMemberId> {
    List<ChannelMember> findByUserId(Long userId);
    List<ChannelMember> findByChannelId(Long channelId);
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    @Modifying
    @Query("delete from ChannelMember member where member.channelId = :channelId")
    void deleteByChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
        UPDATE message_channel_member
        SET last_delivered_message_id = GREATEST(last_delivered_message_id, :messageId)
        WHERE channel_id = :channelId AND user_id = :userId
        """, nativeQuery = true)
    int advanceDelivered(@Param("channelId") Long channelId,
                         @Param("userId") Long userId,
                         @Param("messageId") Long messageId);

    @Modifying
    @Query(value = """
        UPDATE message_channel_member
        SET last_delivered_message_id = GREATEST(last_delivered_message_id, :messageId),
            last_read_message_id = GREATEST(last_read_message_id, :messageId)
        WHERE channel_id = :channelId AND user_id = :userId
        """, nativeQuery = true)
    int advanceRead(@Param("channelId") Long channelId,
                    @Param("userId") Long userId,
                    @Param("messageId") Long messageId);
}
