package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
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
class MessageServiceUnitTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AppUserRepository appUserRepository;

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
    void testProcessAndSaveMessageSuccess() {
        byte[] ciphertext = "encrypted".getBytes();
        when(appUserRepository.findByUsername("sender")).thenReturn(Optional.of(sender));

        messageService.processAndSaveMessage("sender", 10L, 100L, ciphertext);

        verify(messageRepository).save(argThat(msg ->
                msg.getChannelId().equals(10L) &&
                msg.getChannelKeyId().equals(100L) &&
                msg.getSenderId().equals(1L) &&
                msg.getCiphertext() == ciphertext
        ));
    }

    @Test
    void testProcessAndSaveMessageRejectsMissingChannelKey() {
        when(appUserRepository.findByUsername("sender")).thenReturn(Optional.of(sender));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                messageService.processAndSaveMessage("sender", 10L, null, "encrypted".getBytes()));

        assertTrue(exception.getMessage().contains("no active encryption key"),
                "error should tell the client to wait for key rotation");
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testProcessAndSaveMessageUserNotFound() {
        when(appUserRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                messageService.processAndSaveMessage("unknown", 10L, 100L, new byte[0]));
    }
}
