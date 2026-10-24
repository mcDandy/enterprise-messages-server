package cz.upce.fei.ems.backend.unit;


import cz.upce.fei.ems.backend.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.OffsetDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserUnitTest {

    @Test
    void testGettersAndSetters() {
        // Arrange
        AppUser user = new AppUser();
        Long id = 1L;
        String username = "testuser";
        String passwordHash = "hashed_password_123";
        String displayName = "Test User";
        OffsetDateTime now = OffsetDateTime.now();

        // Act
        user.setId(id);
        user.setUsername(username);
        user.setHashedPassword(passwordHash);
        user.setDisplayName(displayName);
        user.setCreatedAt(now);
        user.setBanned(true);
        user.setMainAdmin(true);

        // Assert
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getHashedPassword()).isEqualTo(passwordHash);
        assertThat(user.getDisplayName()).isEqualTo(displayName);
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.isBanned()).isTrue();
        assertThat(user.isMainAdmin()).isTrue();
    }

    @Test
    void testDefaultValues() {
        // Arrange & Act
        AppUser user = new AppUser();

        // Assert - výchozí hodnoty z definice třídy
        assertThat(user.isBanned()).isFalse();
        assertThat(user.isMainAdmin()).isFalse();
    }

    @Test
    void testUserDetailsContract() {
        // Arrange
        AppUser user = new AppUser();
        user.setUsername("john_doe");
        user.setHashedPassword("secure_hash");

        // Act & Assert (Spring Security metody)
        assertThat(user.getUsername()).isEqualTo("john_doe");
        assertThat(user.getPassword()).isEqualTo("secure_hash");

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        assertThat(authorities)
                .hasSize(1)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("user");

        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }
}