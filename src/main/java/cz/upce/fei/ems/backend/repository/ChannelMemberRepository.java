package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, ChannelMemberId> {
    List<ChannelMember> findByUserId(Long userId);
    List<ChannelMember> findByChannelId(Long channelId);
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);
}
