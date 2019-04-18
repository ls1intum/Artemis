package de.tum.in.www1.artemis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

import io.sentry.Sentry;
import io.sentry.SentryClient;

@Configuration
public class SentryConfiguration {

    @Value("${server.url}")
    private String SERVER_URL;

    @Value("${artemis.version}")
    private String VERSION;

    @Value("${info.sentry.dsn}")
    private String SENTRY_DSN;

    SentryConfiguration() {
        initSentry();
    }

    private void initSentry() {
        if (SENTRY_DSN == null) {
            return;
        }

        final SentryClient client = Sentry.init(SENTRY_DSN + "?stacktrace.app.packages=de.tum.in.www1.artemis");
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
        case "https://vmbruegge60.in.tum.de":
            return "e2e";
        default:
            return "local";
        }
    }
}
