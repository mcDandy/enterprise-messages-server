package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Message history endpoints")
public class MessageController {

    private final MessageRepository messageRepository;

    @Operation(
            summary = "Get recent messages",
            description = "Returns the latest 50 messages in the channel for initial rendering."
    )
    @GetMapping("/recent")
    public ResponseEntity<List<Message>> getRecentMessages(@PathVariable Long channelId) {
        List<Message> messages = messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId);
        return ResponseEntity.ok(messages);
    }

    @Operation(
            summary = "Get message history (paginated)",
            description = "Allows infinite scrolling / pagination through channel message history."
    )
    @GetMapping
    public ResponseEntity<Slice<Message>> getMessagesPage(@PathVariable Long channelId, Pageable pageable) {
        Slice<Message> messageSlice = messageRepository.findByChannelId(channelId, pageable);
        return ResponseEntity.ok(messageSlice);
    }
}