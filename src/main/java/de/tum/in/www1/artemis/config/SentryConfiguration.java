package de.tum.in.www1.artemis.config;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerExceptionResolver;

import io.sentry.Sentry;
import io.sentry.SentryClient;

@Configuration
@Profile("prod")
public class SentryConfiguration {

    private final Logger log = LoggerFactory.getLogger(SentryConfiguration.class);

    @Value("${server.url}")
    private String SERVER_URL;

    @Value("${artemis.version}")
    private String VERSION;

    @Value("${info.sentry.dsn}")
    private String SENTRY_DSN;

    @PostConstruct
    public void init() {
        final String dsn = SENTRY_DSN + "?stacktrace.app.packages=de.tum.in.www1.artemis";
        log.info("Sentry DSN: " + dsn);

        if (SENTRY_DSN == null) {
            return;
        }

        final SentryClient client = Sentry.init(dsn);
        client.setRelease(VERSION);
        client.setEnvironment(getEnvironment());
    }

    @Bean
    public HandlerExceptionResolver sentryExceptionResolver() {
        return new io.sentry.spring.SentryExceptionResolver();
    }

    @Bean
    public ServletContextInitializer sentryServletContextInitializer() {
        return new io.sentry.spring.SentryServletContextInitializer();
    }

    private String getEnvironment() {
        switch (SERVER_URL) {
        case "https://artemis.ase.in.tum.de":
            return "prod";
        case "https://artemistest.ase.in.tum.de":
            return "test";
        case "https://artemistest2.ase.in.tum.de":
            return "test";
        case "https://vmbruegge60.in.tum.de":
            return "test";
        default:
            return "local";
        }
    }
}
