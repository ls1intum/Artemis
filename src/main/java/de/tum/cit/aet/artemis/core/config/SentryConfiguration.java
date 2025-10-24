package de.tum.cit.aet.artemis.core.config;

import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import io.sentry.Sentry;
import tech.jhipster.config.JHipsterConstants;

@Configuration
@Lazy
@Profile({ JHipsterConstants.SPRING_PROFILE_PRODUCTION })
public class SentryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SentryConfiguration.class);

    @Value("${artemis.version}")
    private String artemisVersion;

    @Value("${info.sentry.dsn}")
    private Optional<String> sentryDsn;

    @Value("${info.testServer}")
    private Optional<Boolean> isTestServer;

    @Value("${sentry.environment}")
    private Optional<String> environment;

    @Value("${sentry.send-default-pii}")
    private boolean sendDefaultPii;

    /**
     * init sentry with the correct package name and Artemis version
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        if (sentryDsn.isEmpty() || sentryDsn.get().isEmpty()) {
            log.info("Sentry is disabled: Provide a DSN to enable Sentry.");
            return;
        }

        try {
            final String dsn = sentryDsn.get() + "?stacktrace.app.packages=de.tum.cit.aet.artemis";
            log.info("Sentry DSN: {}", dsn);

            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setSendDefaultPii(sendDefaultPii);
                options.setEnvironment(getEnvironment());
                options.setRelease(artemisVersion);
                options.setTracesSampleRate(getTracesSampleRate());
            });
        }
        catch (Exception ex) {
            log.error("Sentry configuration was not successful due to exception!", ex);
        }

    }

    private String getEnvironment() {
        if (environment.isPresent()) {
            return environment.get();
        }
        if (isTestServer.isPresent()) {
            if (isTestServer.get()) {
                return "test";
            }
            else {
                return "prod";
            }
        }
        else {
            return "local";
        }
    }

    /**
     * Get the traces sample rate based on the environment.
     *
     * @return 0% for local, 100% for test, 20% for production environments
     */
    private double getTracesSampleRate() {
        return switch (getEnvironment()) {
            case "prod" -> 0.2;
            case "local" -> 0.0;
            default -> 1.0;
        };
    }
}
