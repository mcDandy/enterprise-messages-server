package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ChannelKeyRepository extends JpaRepository<ChannelKey, Long> {
    Optional<ChannelKey> findByChannelIdAndInvalidatedAtIsNull(Long channelId);
    Optional<ChannelKey> findByChannelIdAndVersion(Long channelId, UUID version);
}
