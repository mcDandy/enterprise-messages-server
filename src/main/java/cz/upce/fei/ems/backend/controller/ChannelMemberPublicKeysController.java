package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.UserKeyResponseDto;
import cz.upce.fei.ems.backend.service.ChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Channel keys")
public class ChannelMemberPublicKeysController {

    private final ChannelService channelService;
    @Operation(
            summary = "Get active public keys for all peers in a channel",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Keys retrieved successfully",
                            content = @Content(schema = @Schema(implementation = UserKeyResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Channel not found", content = @Content)
            }
    )
    @GetMapping("/api/channels/{chId}/keys")
    public ResponseEntity<List<UserKeyResponseDto>> getChannelMemberPublicKeys(@PathVariable Long chId) {
        List<UserKeyResponseDto> keys = channelService.getActivePublicKeysForChannel(chId);
        return ResponseEntity.ok(keys);
    }
}