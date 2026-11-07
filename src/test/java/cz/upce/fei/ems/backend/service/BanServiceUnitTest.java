package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.net.session.SessionRegistry;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
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
class BanServiceUnitTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private BanService banService;

    private AppUser admin;
    private AppUser target;

    @BeforeEach
    void setUp() {
        admin = new AppUser();
        admin.setId(1L);
        admin.setMainAdmin(true);

        target = new AppUser();
        target.setId(2L);
        target.setUsername("target");
        target.setMainAdmin(false);
    }

    @Test
    void testBanUserSuccess() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(target));

        banService.banUserGlobally(1L, 2L);

        assertTrue(target.isBanned());
        verify(appUserRepository).save(target);
        verify(sessionRegistry).removeAndCloseSession("target");
    }

    @Test
    void testBanUserNotAdmin() {
        admin.setMainAdmin(false);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(SecurityException.class, () -> banService.banUserGlobally(1L, 2L));
    }

    @Test
    void testBanSelf() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalStateException.class, () -> banService.banUserGlobally(1L, 1L));
    }
}
