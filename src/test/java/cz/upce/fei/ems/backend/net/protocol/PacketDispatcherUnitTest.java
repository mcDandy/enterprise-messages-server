package cz.upce.fei.ems.backend.net.protocol;

import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacketDispatcherUnitTest {

    @Mock
    private PacketCodec codec;
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private WebSocketSession session;

    @InjectMocks
    private PacketDispatcher packetDispatcher;

    @Test
    void testSendTo() throws Exception {
        Packet packet = new Packet((byte) 0x01, new byte[0]);
        when(session.isOpen()).thenReturn(true);
        when(codec.encode(packet)).thenReturn(ByteBuffer.allocate(1));

        packetDispatcher.sendTo(session, packet);

        verify(session).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void testSendToUser() throws Exception {
        Packet packet = new Packet((byte) 0x01, new byte[0]);
        when(sessionRegistry.getSession("user")).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(codec.encode(packet)).thenReturn(ByteBuffer.allocate(1));

        packetDispatcher.sendToUser("user", packet);

        verify(session).sendMessage(any(BinaryMessage.class));
    }
}
