package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop50ByChannelIdOrderBySentAtDescIdDesc(Long channelId);
    Slice<Message> findByChannelId(Long channelId, Pageable pageable);

    @Modifying
    @Query("delete from Message message where message.channelId = :channelId")
    void deleteByChannelId(@Param("channelId") Long channelId);
}
