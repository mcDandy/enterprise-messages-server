package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.MessageResponseDto;
import cz.upce.fei.ems.backend.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Message history endpoints")
public class MessageController {

    private final MessageService messageService;

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
        return ResponseEntity.ok(messageService.getRecentMessages(channelId));
    }

    @Operation(
            summary = "Get message history (paginated)",
            description = """
                    Returns one slice of channel history. `page` is zero-based (default 0) and `size`
                    is the number of messages per slice (default 50, maximum 100). Results are always
                    ordered by message `id` descending (newest first); custom sorting is not supported.
                    The response is a Slice: use `last` to determine whether another page exists; no
                    total message count is calculated.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of messages retrieved successfully",
                            content = @Content(schema = @Schema(implementation = MessageResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid page or size parameter",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Channel not found",
                            content = @Content)
            }
    )
    @GetMapping
    public ResponseEntity<Slice<MessageResponseDto>> getMessagesPage(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(messageService.getMessagesPage(channelId, page, size));
    }
}
