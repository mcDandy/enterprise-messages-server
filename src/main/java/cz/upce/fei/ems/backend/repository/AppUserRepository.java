package cz.upce.fei.ems.backend.repository;

import cz.upce.fei.ems.backend.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Database queries for application users.
 */

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
}
