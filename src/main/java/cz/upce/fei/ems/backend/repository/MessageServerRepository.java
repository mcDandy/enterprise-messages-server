package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.MessageServer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import cz.upce.fei.ems.backend.domain.ServerType;

public interface MessageServerRepository extends JpaRepository<MessageServer, Long> {
    List<MessageServer> findByType(ServerType type);
}
