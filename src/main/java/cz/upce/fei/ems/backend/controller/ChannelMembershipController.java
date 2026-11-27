package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.ActionResponseDto;
import cz.upce.fei.ems.backend.dto.ChannelKeyEnvelopeDto;
import cz.upce.fei.ems.backend.dto.ChannelMemberResponseDto;
import cz.upce.fei.ems.backend.service.ChannelMembershipService;
import cz.upce.fei.ems.backend.validation.constraints.PositiveId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Exposes operations for listing and managing channel membership.
 */

@RestController
@RequestMapping("/api/channels/{channelId}/members")
@RequiredArgsConstructor
@Tag(name = "Channel Memberships", description = "Managing members inside specific channels")
public class ChannelMembershipController {

    private final ChannelMembershipService channelMembershipService;

    @Operation(
            summary = "Get channel members",
            description = "Lists all members assigned to a specific channel.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of channel members retrieved successfully",
                            content = @Content(schema = @Schema(implementation = ChannelMemberResponseDto.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ChannelMemberResponseDto>> getChannelMembers(
            @PathVariable @PositiveId Long channelId) {
        return ResponseEntity.ok(channelMembershipService.getChannelMembers(channelId));
    }

    @Operation(
            summary = "Add member to channel",
            description = "Adds the single user not currently in the channel and queues encrypted key envelopes "
                    + "for every resulting channel member (Server Admin only).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Member added to channel successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "403", description = "Not authorized",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "Channel not found",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    @PostMapping
    public ResponseEntity<ActionResponseDto> addMemberToChannel(
            @PathVariable @PositiveId Long channelId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "One encrypted key envelope per current member plus the new member.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ChannelKeyEnvelopeDto.class)),
                            examples = @ExampleObject(value = """
                                    [
                                      {"userId": 12, "ciphertext": "AQIDBA=="},
                                      {"userId": 18, "ciphertext": "BQYHCA=="}
                                    ]
                                    """)))
            @Valid @RequestBody List<@Valid ChannelKeyEnvelopeDto> envelopes,
            Principal principal) {
        channelMembershipService.addMemberWithKeyEnvelopes(channelId, envelopes, principal.getName());
        return ResponseEntity.ok(ActionResponseDto.completed());
    }

    @Operation(
            summary = "Remove member from channel",
            description = "Removes a user and queues encrypted key envelopes for every remaining member "
                    + "(Server Admin only).",
            responses = {
                    @ApiResponse(responseCode = "204",
                            description = "Removal completed; succeeds if the membership was already absent"),
                    @ApiResponse(responseCode = "400", description = "Invalid request data",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "403", description = "Not authorized",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "Channel not found",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    @PostMapping("/{userId}/remove")
    public ResponseEntity<Void> removeMemberFromChannel(
            @PathVariable @PositiveId Long channelId,
            @PathVariable @PositiveId Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "One encrypted key envelope per member remaining after removal; may be empty.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ChannelKeyEnvelopeDto.class)),
                            examples = @ExampleObject(value = """
                                    [
                                      {"userId": 12, "ciphertext": "AQIDBA=="}
                                    ]
                                    """)))
            @Valid @RequestBody List<@Valid ChannelKeyEnvelopeDto> envelopes,
            Principal principal) {
        channelMembershipService.removeMemberWithKeyEnvelopes(channelId, userId, envelopes, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
