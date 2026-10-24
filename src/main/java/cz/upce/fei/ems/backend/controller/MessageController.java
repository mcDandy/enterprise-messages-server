package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.dto.MessageResponseDto;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final ChannelKeyRepository channelKeyRepository;

    @Operation(
            summary = "Get recent messages",
            description = "Returns the latest 50 messages in the channel for initial rendering.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Messages retrieved successfully",
                            content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
            }
    )
    @GetMapping("/recent")
    public ResponseEntity<List<MessageResponseDto>> getRecentMessages(@PathVariable Long channelId) {
        List<MessageResponseDto> messages
                = messageRepository.findTop50ByChannelIdOrderBySentAtDescIdDesc(channelId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @Operation(
            summary = "Get message history (paginated)",
            description = "Allows infinite scrolling / pagination through channel message history.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of messages retrieved successfully",
                            content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<Slice<MessageResponseDto>> getMessagesPage(@PathVariable Long channelId, Pageable pageable) {
        Slice<MessageResponseDto> messageSlice = messageRepository.findByChannelId(channelId, pageable)
                .map(this::toDto);
        return ResponseEntity.ok(messageSlice);
    }
    private MessageResponseDto toDto(Message message) {
        MessageResponseDto dto = MessageResponseDto.fromEntity(message);
        dto.setKeyVersion(channelKeyRepository.findById(message.getChannelKeyId())
                .orElseThrow(() -> new IllegalStateException("Missing key version for message " + message.getId()))
                .getVersion());
        return dto;
    }
}
