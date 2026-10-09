package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByServerIdOrderByNameAsc(Long serverId);
    boolean existsByServerIdAndName(Long serverId, String name);
}
