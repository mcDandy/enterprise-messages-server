package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChannelKeyRepository extends JpaRepository<ChannelKey, Long> {
    Optional<ChannelKey> findByChannelIdAndInvalidatedAtIsNull(Long channelId);
    List<ChannelKey> findByChannelIdOrderByVersionDesc(Long channelId);
    Optional<ChannelKey> findByChannelIdAndVersion(Long channelId, Integer version);
}
