package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserRepositoryIntegrationTest {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndLoadUser() {
        String username = "jpa_test_" + UUID.randomUUID();

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setHashedPassword("test_hash_only");
        user.setDisplayName("Test User");

        AppUser saved = userRepository.saveAndFlush(user);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        // Zapomeneme načtené objekty, aby se data
        // skutečně znovu načetla z PostgreSQL.
        entityManager.clear();

        AppUser loaded = userRepository
                .findByUsername(username)
                .orElseThrow();

        assertEquals(saved.getId(), loaded.getId());
        assertEquals(username, loaded.getUsername());
        assertEquals("Test User", loaded.getDisplayName());
        assertEquals("test_hash_only", loaded.getHashedPassword());
    }
}
