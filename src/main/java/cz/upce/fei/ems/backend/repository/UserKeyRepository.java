package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Database queries for current and historical user public keys.
 */

public interface UserKeyRepository extends JpaRepository<UserKey, Long> {
    Optional<UserKey> findByUserIdAndRevokedAtIsNull(Long userId);
    Optional<UserKey> findByUserIdInAndRevokedAtIsNull(List<Long> userId);
    List<UserKey> findByUserIdOrderByCreatedAtDesc(Long userId);
}
