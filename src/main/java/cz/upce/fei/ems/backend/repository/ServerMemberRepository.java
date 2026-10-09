package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ServerMember;
import cz.upce.fei.ems.backend.domain.ServerMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerMemberRepository extends JpaRepository<ServerMember, ServerMemberId> {
    List<ServerMember> findByUserId(Long userId);
    List<ServerMember> findByServerId(Long serverId);
    boolean existsByServerIdAndUserId(Long serverId, Long userId);
}
