package de.tum.cit.aet.artemis.core.config;

import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
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

    @Value("${sentry.send-default-pii:false}")
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

            // For more fine-grained control over what we want to sample, we define a custom traces sampler.
            // By default, it returns null to instead use the tracesSampleRate (as specified per the Sentry documentation).
            // This filters out noise to enable us to focus on the transactions that actually impact Artemis functionality.
            SentryOptions.TracesSamplerCallback tracesSampler = samplingContext -> {
                HttpServletRequest request = (HttpServletRequest) samplingContext.getCustomSamplingContext().get("request");
                String url = request.getRequestURI();
                String method = request.getMethod();
                if (method.equals("HEAD")) {
                    // We're not interested in HEAD requests, so we just drop them (113 transactions per minute)
                    return 0.0;
                }
                if (url.equals("/api/core/public/time")) {
                    // Time endpoint is called very frequently, and we don't want to consider it.
                    return 0.0;
                }
                if (url.equals("/api/iris/status")) {
                    // Iris status is called often enough to warrant downsampling
                    return 0.001;
                }
                if (url.equals("/management/prometheus") || url.equals("/management/info")) {
                    // Management endpoints are not that important for Artemis to function, so we don't sample that often.
                    // Since it's semi-common, we sample less often, but more often than for time
                    return 0.001;
                }

                // If the transactions isn't filtered above and has a parent transaction, we inherit the parent sampling decision.
                Boolean parentSampled = samplingContext.getTransactionContext().getParentSampled();
                if (parentSampled != null) {
                    return parentSampled ? 1.0 : 0.0;
                }

                // If the transaction did not have a parent, default to tracesSampleRate
                return getTracesSampleRate();
            };

            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setSendDefaultPii(sendDefaultPii);
                options.setEnvironment(getEnvironment());
                options.setRelease(artemisVersion);
                options.setTracesSampleRate(getTracesSampleRate()); // configured as a fallback
                options.setTracesSampler(tracesSampler);
            });
        }
        catch (Exception ex) {
            log.error("Sentry configuration was not successful due to exception!", ex);
        }

    }

    private String getEnvironment() {
        if (environment.isPresent() && !environment.get().isBlank()) {
            return environment.get().trim().toLowerCase();
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
     * @return 0% for local, 100% for test and staging, 5% for production environments
     */
    private double getTracesSampleRate() {
        String env = getEnvironment();
        // All test/staging environments get 1.0 sample rate
        if (env.startsWith("test") || env.startsWith("staging")) {
            return 1.0;
        }

        // Only "prod" get 0.05, all others (like local) are disabled
        return switch (env) {
            case "prod" -> 0.05;
            default -> 0.0;
        };
    }
}
