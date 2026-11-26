package cz.upce.fei.ems.backend.config;

import cz.upce.fei.ems.backend.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Provides authentication, user lookup, password encoding, and strict JSON mapping beans.
 */

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final AppUserRepository userRepository;

    /** Loads application users by username for Spring Security authentication. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username ->
                userRepository.findByUsername(username).orElseThrow(()
                        -> new UsernameNotFoundException("User not found"));
    }

    /** Provides the password encoder used when storing and checking credentials. */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Exposes Spring Security’s configured authentication manager. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    /** Enables strict duplicate-key detection and disables permissive scalar coercion for JSON input. */
    @Bean
    public JsonMapperBuilderCustomizer strictJsonInput() {
        return builder -> builder
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
    }
}
