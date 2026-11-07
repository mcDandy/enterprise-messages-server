package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ChannelKeyRepository extends JpaRepository<ChannelKey, Long> {
    Optional<ChannelKey> findByChannelIdAndInvalidatedAtIsNull(Long channelId);
    Optional<ChannelKey> findByChannelIdAndVersion(Long channelId, UUID version);

    @Modifying
    @Query("delete from ChannelKey key where key.channelId = :channelId")
    void deleteByChannelId(@Param("channelId") Long channelId);
}
