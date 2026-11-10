package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.queue.QueuedPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueuedKeyDeliveryService {
    private final PostgresUserQueue userQueue;
    private final PacketCodec packetCodec;
    private final PacketDispatcher dispatcher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNextQueuedPacket(long recipientUserId, String username, WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        var pendingPackets = userQueue.peekFirstForUser(recipientUserId);
        if (pendingPackets.isEmpty()) {
            return;
        }

        QueuedPacket pending = pendingPackets.getFirst();
        try {
            Packet packet = packetCodec.decode(ByteBuffer.wrap(pending.frame()));
            dispatcher.sendTo(session, packet);
        } catch (Exception e) {
            // Leave the row in the queue so a later delivery attempt can retry it.
            log.warn("Could not dispatch queued packet {} to {}", pending.messageId(), username, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acknowledgeAndSendNext(long recipientUserId, String username, WebSocketSession session) {
        if (!userQueue.acknowledgeFirstForUser(recipientUserId)) {
            log.debug("Ignoring acknowledgment from {} with an empty packet queue", username);
            return;
        }
        runAfterCommit(() -> sendNextQueuedPacket(recipientUserId, username, session));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
