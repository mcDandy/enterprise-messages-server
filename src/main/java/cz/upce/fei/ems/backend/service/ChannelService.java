package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelKeyRepository channelKeyRepository;


    @Transactional(readOnly = true)
    public byte[] getActiveChannelKeyHash(Long channelId) {
        ChannelKey activeKey = channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Active key not found for channel ID: " + channelId));
        return activeKey.getKeyHash();
    }


    @Transactional
    public void rotateChannelKey(Long channelId, byte[] newKeyHash) {
        // 1. Invalidate the current active key if it exists
        channelKeyRepository.findByChannelIdAndInvalidatedAtIsNull(channelId).ifPresent(oldKey -> {
            oldKey.setInvalidatedAt(OffsetDateTime.now());
            channelKeyRepository.save(oldKey);
        });

        // 2. Determine the version number for the new key
        List<ChannelKey> existingKeys = channelKeyRepository.findByChannelIdOrderByVersionDesc(channelId);
        int nextVersion = existingKeys.isEmpty() ? 1 : existingKeys.get(0).getVersion() + 1;

        // 3. Create and save the new key record
        ChannelKey newChannelKey = new ChannelKey();
        newChannelKey.setChannelId(channelId);
        newChannelKey.setVersion(nextVersion);
        newChannelKey.setKeyHash(newKeyHash);

        channelKeyRepository.save(newChannelKey);
    }
}