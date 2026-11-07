package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageReceiptRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageReceiptServiceUnitTest {

    @Mock
    private MessageReceiptRepository messageReceiptRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @InjectMocks
    private MessageReceiptService messageReceiptService;

    private AppUser user;
    private Message message;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("testuser");

        message = new Message();
        message.setId(10L);
        message.setChannelId(100L);
    }

    @Test
    void testMarkAsDeliveredSuccess() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(channelMemberRepository.existsByChannelIdAndUserId(100L, 1L)).thenReturn(true);

        messageReceiptService.markAsDelivered(10L, "testuser");

        verify(messageReceiptRepository).upsertDelivered(100L, 1L, 10L);
    }

    @Test
    void testMarkAsReadForbidden() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(channelMemberRepository.existsByChannelIdAndUserId(100L, 1L)).thenReturn(false);

        assertThrows(SecurityException.class, () -> messageReceiptService.markAsRead(10L, "testuser"));
    }
}
