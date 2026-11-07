package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.UserKey;
import cz.upce.fei.ems.backend.dto.UserResponseDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.UserKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserKeyRepository userKeyRepository;

    @InjectMocks
    private UserService userService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
    }

    @Test
    void testUpdateDisplayNameSuccess() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        userService.updateDisplayName("testuser", "New Name");

        assertEquals("New Name", user.getDisplayName());
        verify(appUserRepository).save(user);
    }

    @Test
    void testUpdateDisplayNameNotFound() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> userService.updateDisplayName("testuser", "New Name"));
    }

    @Test
    void testRotatePublicKeySuccess() {
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        UserKey oldKey = new UserKey();
        oldKey.setUserId(1L);
        oldKey.setPublicKey("old-key");
        when(userKeyRepository.findByUserIdAndRevokedAtIsNull(1L)).thenReturn(Optional.of(oldKey));

        userService.rotatePublicKey("testuser", "new-key");

        assertNotNull(oldKey.getRevokedAt());
        verify(userKeyRepository).save(oldKey);
        verify(userKeyRepository).save(argThat(key -> "new-key".equals(key.getPublicKey())));
    }

    @Test
    void testUserResponseFromEntity() {
        UserResponseDto dto = UserResponseDto.fromEntity(user);
        assertEquals(user.getId(), dto.getId());
        assertEquals(user.getUsername(), dto.getUsername());
        assertEquals(user.getDisplayName(), dto.getDisplayName());
    }
}
