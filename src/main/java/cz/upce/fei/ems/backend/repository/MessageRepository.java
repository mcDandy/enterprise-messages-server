package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop50ByChannelIdOrderBySentAtDescIdDesc(Long channelId);
    Slice<Message> findByChannelId(Long channelId, Pageable pageable);
}
