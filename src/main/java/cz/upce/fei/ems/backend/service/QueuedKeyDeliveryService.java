package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueuedKeyDeliveryService {
    private final PostgresUserQueue userQueue;
    private final PacketCodec packetCodec;
    private final PacketDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;
    private final ConcurrentHashMap<Long, Object> deliveryLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, InFlightDelivery> inFlightDeliveries = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNextQueuedPacket(long recipientUserId, String username, WebSocketSession session) {
        synchronized (deliveryLocks.computeIfAbsent(recipientUserId, ignored -> new Object())) {
            if (!isActiveSession(username, session)) {
                return;
            }
            var pendingPackets = userQueue.peekFirstForUser(recipientUserId);
            if (pendingPackets.isEmpty()) {
                inFlightDeliveries.remove(recipientUserId);
                return;
            }

            QueuedPacket pending = pendingPackets.getFirst();
            InFlightDelivery inFlight = inFlightDeliveries.get(recipientUserId);
            if (inFlight != null && inFlight.matches(pending.messageId(), session)) {
                return;
            }

            try {
                Packet packet = packetCodec.decode(ByteBuffer.wrap(pending.frame()));
                dispatcher.sendTo(session, packet);
                inFlightDeliveries.put(recipientUserId, new InFlightDelivery(pending.messageId(), session));
            } catch (Exception e) {
                // Leave the row in the queue so a later delivery attempt can retry it.
                log.warn("Could not dispatch queued packet {} to {}", pending.messageId(), username, e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acknowledgeAndSendNext(long recipientUserId, String username, WebSocketSession session) {
        synchronized (deliveryLocks.computeIfAbsent(recipientUserId, ignored -> new Object())) {
            if (!isActiveSession(username, session)) {
                log.debug("Ignoring acknowledgment from stale session for {}", username);
                return;
            }
            InFlightDelivery inFlight = inFlightDeliveries.get(recipientUserId);
            if (inFlight == null || !inFlight.belongsTo(session)) {
                log.debug("Ignoring acknowledgment from {} without an in-flight packet", username);
                return;
            }
            if (!userQueue.acknowledgeForUser(recipientUserId, inFlight.messageId())) {
                log.debug("Acknowledgment from {} did not match a queued packet", username);
                inFlightDeliveries.remove(recipientUserId, inFlight);
                return;
            }
            runAfterCommit(
                    () -> {
                        inFlightDeliveries.remove(recipientUserId, inFlight);
                        sendNextQueuedPacket(recipientUserId, username, session);
                    },
                    () -> inFlightDeliveries.remove(recipientUserId, inFlight)
            );
        }
    }

    private boolean isActiveSession(String username, WebSocketSession session) {
        return session != null && session.isOpen() && sessionRegistry.getSession(username) == session;
    }

    private void runAfterCommit(Runnable action, Runnable onRollback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        onRollback.run();
                    }
                }
            });
        } else {
            action.run();
        }
    }

    private record InFlightDelivery(long messageId, WebSocketSession session) {
        private boolean matches(long candidateMessageId, WebSocketSession candidateSession) {
            return messageId == candidateMessageId && belongsTo(candidateSession);
        }

        private boolean belongsTo(WebSocketSession candidateSession) {
            return session == candidateSession;
        }
    }
}
