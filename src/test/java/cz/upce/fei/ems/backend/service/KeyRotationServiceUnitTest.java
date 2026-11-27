package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelKey;
import cz.upce.fei.ems.backend.net.protocol.PacketCodec;
import cz.upce.fei.ems.backend.net.protocol.Packet;
import cz.upce.fei.ems.backend.net.protocol.PacketType;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.queue.PostgresUserQueue;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelKeyRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class KeyRotationServiceUnitTest {
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private ChannelKeyRepository channelKeyRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PostgresUserQueue userQueue;
    @org.mockito.Spy
    private PacketCodec packetCodec = new PacketCodec();
    @Mock
    private QueuedKeyDeliveryService keyDeliveryService;

    @InjectMocks
    private KeyRotationService keyRotationService;

    @Test
    void rotationCreatesOneVersionAndQueuesRecipientSpecificCiphertexts() {
        ChannelMember member2 = new ChannelMember();
        member2.setUserId(2L);
        ChannelMember member3 = new ChannelMember();
        member3.setUserId(3L);
        when(channelMemberRepository.findByChannelId(10L)).thenReturn(List.of(member2, member3));
        when(appUserRepository.existsById(2L)).thenReturn(true);
        when(appUserRepository.existsById(3L)).thenReturn(true);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(user(2L, "user2")));
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(user(3L, "user3")));
        when(channelKeyRepository.save(any(ChannelKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userQueue.enqueueForUser(anyLong(), any(byte[].class))).thenReturn(1L, 2L);

        Map<Long, byte[]> ciphertexts = new LinkedHashMap<>();
        ciphertexts.put(2L, new byte[]{2});
        ciphertexts.put(3L, new byte[]{3});
        keyRotationService.rotateAndDistributeKey(10L, 1L, "admin", ciphertexts);

        ArgumentCaptor<Long> recipientCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<byte[]> frameCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(userQueue, times(2)).enqueueForUser(recipientCaptor.capture(), frameCaptor.capture());
        assertEquals(List.of(2L, 3L), recipientCaptor.getAllValues());

        Packet first = packetCodec.decode(ByteBuffer.wrap(frameCaptor.getAllValues().get(0)));
        Packet second = packetCodec.decode(ByteBuffer.wrap(frameCaptor.getAllValues().get(1)));
        assertEquals(PacketType.UNICAST_KEY_ROTATION.getCode(), first.getType());
        assertEquals(PacketType.UNICAST_KEY_ROTATION.getCode(), second.getType());
        ByteBuffer firstPayload = ByteBuffer.wrap(first.getPayload());
        ByteBuffer secondPayload = ByteBuffer.wrap(second.getPayload());
        assertEquals(1L, firstPayload.getLong());
        assertEquals(1L, secondPayload.getLong());
        assertEquals(firstPayload.getLong(), secondPayload.getLong());
        assertEquals(firstPayload.getLong(), secondPayload.getLong());
        assertEquals(2, firstPayload.get());
        assertEquals(3, secondPayload.get());
        verify(channelKeyRepository).save(any(ChannelKey.class));
    }

    private static AppUser user(long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
