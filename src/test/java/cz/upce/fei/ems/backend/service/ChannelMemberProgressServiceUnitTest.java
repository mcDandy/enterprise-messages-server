package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.ChannelMember;
import cz.upce.fei.ems.backend.domain.ChannelMemberId;
import cz.upce.fei.ems.backend.domain.Message;
import cz.upce.fei.ems.backend.repository.ChannelMemberRepository;
import cz.upce.fei.ems.backend.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelMemberProgressServiceUnitTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private ChannelMemberInfoService channelMemberInfoService;

    @InjectMocks
    private ChannelMemberProgressService progressService;

    @Test
    void markAsDeliveredAdvancesAndPublishesProgress() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L)));
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(true);
        when(channelMemberRepository.advanceDelivered(1L, 2L, 10L)).thenReturn(1);
        when(channelMemberRepository.findById(new ChannelMemberId(1L, 2L)))
                .thenReturn(Optional.of(new ChannelMember()));

        progressService.markAsDelivered(10L, 2L);

        verify(channelMemberRepository).advanceDelivered(1L, 2L, 10L);
        verify(channelMemberInfoService).publishToChannelMembers(any(ChannelMember.class));
    }

    @Test
    void markAsReadAdvancesAndPublishesProgress() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L)));
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(true);
        when(channelMemberRepository.advanceRead(1L, 2L, 10L)).thenReturn(1);
        when(channelMemberRepository.findById(new ChannelMemberId(1L, 2L)))
                .thenReturn(Optional.of(new ChannelMember()));

        progressService.markAsRead(10L, 2L);

        verify(channelMemberRepository).advanceRead(1L, 2L, 10L);
        verify(channelMemberInfoService).publishToChannelMembers(any(ChannelMember.class));
    }

    @Test
    void markAsReadRejectsNonMember() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(10L, 1L)));
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(false);

        assertThrows(SecurityException.class, () -> progressService.markAsRead(10L, 2L));
        verify(channelMemberRepository, never()).advanceRead(anyLong(), anyLong(), anyLong());
    }

    private Message message(long id, long channelId) {
        Message message = new Message();
        message.setId(id);
        message.setChannelId(channelId);
        return message;
    }
}
