package cz.upce.fei.ems.backend.unit;

import cz.upce.fei.ems.backend.domain.UserKey;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class UserKeyUnitTest {

    @Test
    void testGettersAndSetters() {
        UserKey userKey = new UserKey();
        Long id = 1L;
        Long userId = 42L;
        String publicKey = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----";
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime revoked = now.plusDays(30);

        userKey.setId(id);
        userKey.setUserId(userId);
        userKey.setPublicKey(publicKey);
        userKey.setCreatedAt(now);
        userKey.setRevokedAt(revoked);

        assertThat(userKey.getId()).isEqualTo(id);
        assertThat(userKey.getUserId()).isEqualTo(userId);
        assertThat(userKey.getPublicKey()).isEqualTo(publicKey);
        assertThat(userKey.getCreatedAt()).isEqualTo(now);
        assertThat(userKey.getRevokedAt()).isEqualTo(revoked);
    }
}