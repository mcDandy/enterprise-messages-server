package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.ChannelKeyPending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelKeyPendingRepository extends JpaRepository<ChannelKeyPending, Long> {

    List<ChannelKeyPending> findByRecipientUserId(Long recipientUserId);
}