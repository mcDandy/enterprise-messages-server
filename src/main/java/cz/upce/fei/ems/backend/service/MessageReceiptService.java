package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageReceipt;
import cz.upce.fei.ems.backend.domain.MessageReceiptId;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class MessageReceiptService {

    private final MessageReceiptRepository messageReceiptRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public void markAsDelivered(Long messageId, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        MessageReceiptId receiptId = new MessageReceiptId(messageId, user.getId());
        MessageReceipt receipt = messageReceiptRepository.findById(receiptId)
                .orElseGet(() -> {
                    MessageReceipt newReceipt = new MessageReceipt();
                    newReceipt.setMessageId(messageId);
                    newReceipt.setUserId(user.getId());
                    return newReceipt;
                });

        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(OffsetDateTime.now());
            messageReceiptRepository.save(receipt);
        }
    }

    @Transactional
    public void markAsRead(Long messageId, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        MessageReceiptId receiptId = new MessageReceiptId(messageId, user.getId());
        MessageReceipt receipt = messageReceiptRepository.findById(receiptId)
                .orElseGet(() -> {
                    MessageReceipt newReceipt = new MessageReceipt();
                    newReceipt.setMessageId(messageId);
                    newReceipt.setUserId(user.getId());
                    return newReceipt;
                });

        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(OffsetDateTime.now());
        }
        if (receipt.getReadAt() == null) {
            receipt.setReadAt(OffsetDateTime.now());
            messageReceiptRepository.save(receipt);
        }
    }
}