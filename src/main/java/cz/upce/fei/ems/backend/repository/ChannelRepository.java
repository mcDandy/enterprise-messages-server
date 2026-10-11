package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.Channel;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByServerIdOrderByNameAsc(Long serverId);
    boolean existsByServerIdAndName(Long serverId, String name);
    @Query("SELECT c FROM Channel c WHERE c.serverId = :serverId AND c.id IN " +
            "(SELECT cm.channelId FROM ChannelMember cm WHERE cm.userId = :userId)")
    List<Channel> findAccessibleChannelsByServerIdAndUserId(
            @Param("serverId") Long serverId, @Param("userId") Long userId
    );
}
