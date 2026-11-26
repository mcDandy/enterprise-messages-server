package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;
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
import java.util.Set;

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
    private ChannelRepository channelRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private PostgresUserQueue userQueue;
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private QueuedKeyDeliveryService queuedPacketDeliveryService;
    @Mock
    private WebSocketSession recipientSession;
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
        AppUser recipient = new AppUser();
        recipient.setId(2L);
        recipient.setUsername("recipient");
        when(appUserRepository.findByUsername("sender")).thenReturn(Optional.of(sender));
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });
        when(channelMemberRepository.findByChannelId(10L))
                .thenReturn(List.of(senderMembership, recipientMembership));
        when(appUserRepository.findAllById(Set.of(2L))).thenReturn(List.of(recipient));
        when(sessionRegistry.getSession("recipient")).thenReturn(recipientSession);
        when(recipientSession.isOpen()).thenReturn(true);

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
        saveBeforeQueue.verify(userQueue).enqueueMessageForUser(eq(2L), eq(55L), queuedFrame.capture());
        verify(userQueue, never()).enqueueMessageForUser(eq(sender.getId()), anyLong(), any(byte[].class));
        verify(queuedPacketDeliveryService).sendNextQueuedPacket(2L, "recipient", recipientSession);
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

    @Test
    void getRecentMessagesRejectsMissingChannel() {
        when(channelRepository.existsById(999L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.getRecentMessages(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(messageRepository, never()).findTop50ByChannelIdOrderBySentAtDescIdDesc(999L);
    }

    @Test
    void getMessagesPageRejectsMissingChannel() {
        when(channelRepository.existsById(999L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesPage(999L, 0, 50));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(messageRepository, never()).findByChannelId(eq(999L), any());
    }

    @Test
    void getMessagesPageRejectsInvalidPageAndSize() {
        ResponseStatusException negativePage = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesPage(999L, -1, 50));
        ResponseStatusException zeroSize = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesPage(999L, 0, 0));
        ResponseStatusException excessiveSize = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesPage(999L, 0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, negativePage.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, zeroSize.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, excessiveSize.getStatusCode());
        verifyNoInteractions(channelRepository, messageRepository);
    }
}
