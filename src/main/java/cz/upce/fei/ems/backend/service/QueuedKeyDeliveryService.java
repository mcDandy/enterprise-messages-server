package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.queue.PostgresKeyQueue;
import cz.upce.fei.ems.backend.queue.QueuedKeyPacket;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

/** Deliver after the producer commits, or on reconnect. */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueuedKeyDeliveryService {
    private static final int VISIBILITY_SECONDS = 60;
    private static final int BATCH_SIZE = 100;

    private final PostgresKeyQueue keyQueue;
    private final ChannelMemberRepository channelMemberRepository;
    private final PacketCodec packetCodec;
    private final PacketDispatcher dispatcher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverQueuedKeys(long recipientUserId, String username, WebSocketSession session) {
        for (QueuedKeyPacket pending : keyQueue.claimForUser(
                recipientUserId, VISIBILITY_SECONDS, BATCH_SIZE)) {
            if (!channelMemberRepository.existsByChannelIdAndUserId(pending.channelId(), recipientUserId)) {
                keyQueue.acknowledgeForUser(pending.messageId());
                continue;
            }
            if (!session.isOpen()) break;
            try {
                Packet packet = packetCodec.decode(ByteBuffer.wrap(pending.frame()));
                dispatcher.sendTo(session, packet);
                keyQueue.acknowledgeForUser(pending.messageId());
            } catch (Exception e) {
                // Leave the row in the queue; after timeout a future delivery can retry.
                log.warn("Could not dispatch queued key packet {} to {}", pending.messageId(), username, e);
            }
        }
    }
}
