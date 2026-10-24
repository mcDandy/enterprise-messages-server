package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.dto.MessageWithVersionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Message history endpoints")
public class MessageController {

    private final MessageRepository messageRepository;
    private final ChannelKeyRepository channelKeyRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final AppUserRepository appUserRepository;

    @Operation(
            summary = "Get recent messages",
            description = "Returns the latest 50 messages in the channel for initial rendering."
    )
    @GetMapping("/recent")
    public ResponseEntity<List<MessageWithVersionDto>> getRecentMessages(@PathVariable Long channelId, Principal principal) {
        requireMember(channelId, principal);
        List<Message> messages = messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId);
        return ResponseEntity.ok(messages.stream().map(this::toDto).toList());
    }

    @Operation(
            summary = "Get message history (paginated)",
            description = "Allows infinite scrolling / pagination through channel message history."
    )
    @GetMapping
    public ResponseEntity<Slice<MessageWithVersionDto>> getMessagesPage(@PathVariable Long channelId, Pageable pageable, Principal principal) {
        requireMember(channelId, principal);
        Slice<Message> messageSlice = messageRepository.findByChannelId(channelId, pageable);
        return ResponseEntity.ok(messageSlice.map(this::toDto));
    }

    private void requireMember(Long channelId, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var user = appUserRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this channel.");
        }
    }

    private MessageWithVersionDto toDto(Message message) {
        var version = channelKeyRepository.findById(message.getChannelKeyId())
                .orElseThrow(() -> new IllegalStateException("Missing channel key version for message " + message.getId()));
        return new MessageWithVersionDto(
                message.getId(), message.getChannelId(), message.getSenderId(),
                message.getCiphertext(), message.getSentAt(), message.getChannelKeyId(), version.getVersion());
    }
}
