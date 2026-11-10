package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceUnitTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private PostgresUserQueue userQueue;
    @Spy
    private PacketCodec packetCodec = new PacketCodec();

    @InjectMocks
    private MessageService messageService;

    private AppUser sender;

    @BeforeEach
    void setUp() {
        sender = new AppUser();
        sender.setId(1L);
        sender.setUsername("sender");
    }

    @Test
    void testProcessAndQueueMessageSavesAndQueuesOtherChannelMembers() {
        byte[] ciphertext = "encrypted".getBytes();
        Packet packet = new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(),
                new byte[]{0, 0, 0, 10, 1, 2});
        ChannelMember senderMembership = new ChannelMember();
        senderMembership.setUserId(sender.getId());
        ChannelMember recipientMembership = new ChannelMember();
        recipientMembership.setUserId(2L);
        when(appUserRepository.findByUsername("sender")).thenReturn(Optional.of(sender));
        when(channelMemberRepository.findByChannelId(10L))
                .thenReturn(List.of(senderMembership, recipientMembership));

        messageService.processAndQueueMessage("sender", 10L, 100L, ciphertext, packet);

        verify(messageRepository).saveAndFlush(argThat(msg ->
                msg.getChannelId().equals(10L) &&
                msg.getChannelKeyId().equals(100L) &&
                msg.getSenderId().equals(1L) &&
                msg.getCiphertext() == ciphertext
        ));
        ArgumentCaptor<byte[]> queuedFrame = ArgumentCaptor.forClass(byte[].class);
        InOrder saveBeforeQueue = inOrder(messageRepository, userQueue);
        saveBeforeQueue.verify(messageRepository).saveAndFlush(any(Message.class));
        saveBeforeQueue.verify(userQueue).enqueueForUser(eq(2L), queuedFrame.capture());
        verify(userQueue, never()).enqueueForUser(eq(sender.getId()), any(byte[].class));
        Packet decoded = packetCodec.decode(java.nio.ByteBuffer.wrap(queuedFrame.getValue()));
        assertEquals(packet.getType(), decoded.getType());
        assertArrayEquals(packet.getPayload(), decoded.getPayload());
    }

    @Test
    void testProcessAndQueueMessageRejectsMissingChannelKey() {
        when(appUserRepository.findByUsername("sender")).thenReturn(Optional.of(sender));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                messageService.processAndQueueMessage("sender", 10L, null, "encrypted".getBytes(),
                        new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[0])));

        assertTrue(exception.getMessage().contains("no active encryption key"),
                "error should tell the client to wait for key rotation");
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testProcessAndQueueMessageUserNotFound() {
        when(appUserRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                messageService.processAndQueueMessage("unknown", 10L, 100L, new byte[0],
                        new Packet(PacketType.CHAT_MESSAGE_ENC.getCode(), new byte[0])));
    }
}
