package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelKeyRepository channelKeyRepository;

    @Transactional(readOnly = true)
    public ChannelKey getActiveChannelKeyVersion(Long channelId) {
        return channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active key version not found for channel ID: " + channelId));
    }

    /**
     * Stores public metadata only. The actual E2EE key is generated and retained by clients.
     * The caller must ensure that an authorized channel member completed key negotiation.
     */
    @Transactional
    public ChannelKey rotateChannelKey(Long channelId, UUID newVersion) {
        if (newVersion == null) {
            throw new IllegalArgumentException("Key version UUID is required.");
        }
        if (!channelRepository.existsById(channelId)) {
            throw new IllegalArgumentException("Channel not found: " + channelId);
        }
        if (channelKeyRepository.findByChannelIdAndVersion(channelId, newVersion).isPresent()) {
            throw new IllegalArgumentException("Key version already exists in channel: " + newVersion);
        }

        channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).ifPresent(previous -> {
            previous.setInvalidatedAt(OffsetDateTime.now());
            channelKeyRepository.saveAndFlush(previous);
        });

        ChannelKey next = new ChannelKey();
        next.setChannelId(channelId);
        next.setVersion(newVersion);
        return channelKeyRepository.saveAndFlush(next);
    }
}
