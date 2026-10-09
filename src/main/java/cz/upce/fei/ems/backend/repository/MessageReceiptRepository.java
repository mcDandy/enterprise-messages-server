package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.MessageReceipt;
import cz.upce.fei.ems.backend.domain.MessageReceiptId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, MessageReceiptId> {
    List<MessageReceipt> findByUserIdAndReadAtIsNull(Long userId);
    List<MessageReceipt> findByMessageId(Long messageId);
}
