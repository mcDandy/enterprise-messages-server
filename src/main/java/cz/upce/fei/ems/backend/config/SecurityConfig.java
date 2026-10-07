package cz.upce.fei.ems.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").permitAll() //todo Testovací průchod pro WebSockety bez hesla
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable) // Pro WS a OAuth2 API je běžné vypnout ochranu CSRF
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(Customizer.withDefaults()
                        ));

        return http.build();
    }
}