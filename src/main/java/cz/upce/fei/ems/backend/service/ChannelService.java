package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelKeyRepository channelKeyRepository;

    @Transactional(readOnly = true)
    public ChannelKey getActiveChannelKeyVersion(Long channelId) {
        return channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Active key version not found: " + channelId));
    }

    @Transactional
    public void rotateChannelKey(Long channelId, UUID newVersion) {
        channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).ifPresent(oldKey -> {
            oldKey.setInvalidatedAt(OffsetDateTime.now());
            channelKeyRepository.saveAndFlush(oldKey);
        });

        ChannelKey newChannelKey = new ChannelKey();
        newChannelKey.setChannelId(channelId);
        newChannelKey.setVersion(newVersion);
        channelKeyRepository.save(newChannelKey);
    }
}
