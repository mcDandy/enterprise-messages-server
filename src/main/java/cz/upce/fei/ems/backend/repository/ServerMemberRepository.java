package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.MessageServerMember;
import cz.upce.fei.ems.backend.domain.MessageServerMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends JpaRepository<MessageServerMember, MessageServerMemberId> {
    List<MessageServerMember> findByUserId(Long userId);
    List<MessageServerMember> findByServerId(Long serverId);
    boolean existsByServerIdAndUserId(Long serverId, Long userId);
    Optional<MessageServerMember> findByServerIdAndUserId(Long serverId, Long userId);
}
