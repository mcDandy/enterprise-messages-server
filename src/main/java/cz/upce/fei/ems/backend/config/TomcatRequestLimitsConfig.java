package cz.upce.fei.ems.backend.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatRequestLimitsConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatHeaderCountLimit() {
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setProperty("maxHeaderCount", "100"));
    }
}
