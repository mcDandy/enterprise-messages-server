package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketDispatcher;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Builds and delivers channel-member progress packets to online channel members.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelMemberInfoService {

    private final AppUserRepository appUserRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final PacketDispatcher packetDispatcher;
    private final SessionRegistry sessionRegistry;

    /**
     * Publishes the updated member’s channel ID, user ID, delivered ID, and read ID to online channel members.
     * Delivery is deferred until the surrounding transaction commits when transaction synchronization is active.
     *
     * @param updatedMember persisted membership whose progress should be broadcast
     */
    public void publishToChannelMembers(ChannelMember updatedMember) {
        List<Long> memberUserIds = userQueueRecipients(updatedMember.getChannelId());
        List<AppUser> members = appUserRepository.findAllById(memberUserIds);
        Packet packet = createPacket(updatedMember);
        runAfterCommit(() -> dispatchToOnlineMembers(updatedMember, members, packet));
    }

    private List<Long> userQueueRecipients(Long channelId) {
        // Membership IDs are obtained from the DB to avoid depending on session state.
        return channelMemberRepository.findByChannelId(channelId).stream()
                .map(ChannelMember::getUserId)
                .toList();
    }

    private Packet createPacket(ChannelMember member) {
        ByteBuffer payload = ByteBuffer.allocate(4 * Long.BYTES);
        payload.putLong(member.getChannelId());
        payload.putLong(member.getUserId());
        payload.putLong(member.getLastDeliveredMessageId() == null ? 0L : member.getLastDeliveredMessageId());
        payload.putLong(member.getLastReadMessageId() == null ? 0L : member.getLastReadMessageId());
        return new Packet(PacketType.CHANNEL_MEMBER_INFO.getCode(), payload.array());
    }

    private void dispatchToOnlineMembers(ChannelMember updatedMember, List<AppUser> members, Packet packet) {
        for (AppUser member : members) {
            WebSocketSession session = sessionRegistry.getSession(member.getUsername());
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                packetDispatcher.sendTo(session, packet);
                log.debug("Sent channel member info: channelId={}, memberUserId={}, recipientUserId={}",
                        updatedMember.getChannelId(), updatedMember.getUserId(), member.getId());
            } catch (Exception e) {
                log.warn("Could not send channel member info to online user {}", member.getId(), e);
            }
        }
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
