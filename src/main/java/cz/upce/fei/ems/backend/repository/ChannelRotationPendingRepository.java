package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelRotationPending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelRotationPendingRepository extends JpaRepository<ChannelRotationPending, Long> {
    Optional<ChannelRotationPending> findByChannelId(Long channelId);
    void deleteByChannelId(Long channelId);
}