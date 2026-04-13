package de.tum.cit.aet.artemis.core.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SamplingContext;
import io.sentry.Sentry;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryTransaction;
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
            // By default, it uses getTracesSampleRate() as the sampling rate; tracesSampler overrides tracesSampleRate.
            // This filters out noise to enable us to focus on the transactions that actually impact Artemis functionality.

            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setEnvironment(getEnvironment());
                options.setRelease(artemisVersion);
                options.setTracesSampleRate(getTracesSampleRate()); // configured as a fallback
                options.setTracesSampler(this::tracesSampler);
                options.setBeforeSend((e, _) -> (SentryEvent) (scrubData(e)));
                options.setBeforeSendTransaction((e, _) -> (SentryTransaction) (scrubData(e)));
                options.setBeforeBreadcrumb(this::scrubBreadcrumb);
            });
        }
        catch (Exception ex) {
            log.error("Sentry configuration was not successful due to exception!", ex);
        }

    }

    private double tracesSampler(SamplingContext samplingContext) {
        double defaultSampleRate = getTracesSampleRate();
        if (samplingContext.getCustomSamplingContext() == null) {
            return defaultSampleRate;
        }
        Object customSamplingRequest = samplingContext.getCustomSamplingContext().get("request");
        Boolean parentSampled = samplingContext.getTransactionContext().getParentSampled();

        // Guard against other types of request; we want these to use defaultSampleRate
        if (!(customSamplingRequest instanceof HttpServletRequest request)) {
            if (parentSampled != null) {
                return parentSampled ? 1.0 : 0.0;
            }
            return defaultSampleRate;
        }
        String url = request.getRequestURI();
        String method = request.getMethod();
        if ("HEAD".equals(method)) {
            // We're not interested in HEAD requests, so we just drop them (113 transactions per minute)
            return 0.0;
        }
        if (url.equals("/api/public/time")) {
            // Defensive: /time is normally handled by PublicTimeValve before reaching Spring,
            // but we keep this filter in case the valve is removed or bypassed.
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
        if (parentSampled != null) {
            return parentSampled ? 1.0 : 0.0;
        }

        // If the transaction did not have a parent, default to tracesSampleRate
        return defaultSampleRate;
    }

    private String scrubStringMessage(@NotNull String unscrubbed) {
        // Scrub user data from string
        // Patterns:
        // - user=barney_young => user=\S+
        // - User{...} => User{[^}]*}
        // - emails => [A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}
        List<String> piiPatterns = List.of("user=\\S+", "User{[^}]*}", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        for (String pattern : piiPatterns) {
            unscrubbed = unscrubbed.replaceAll(pattern, "");
        }
        return unscrubbed;
    }

    private String scrubUrl(@NotNull String unscrubbed) {
        // The user login is part of repository names.
        // To ensure we're not accidentally transmitting them,
        // we use a heuristic to filter out this part of the URL.
        // We assume the part between the last dash and .git to contain a username.
        String scrubbed = unscrubbed.replaceAll("\\/git\\/([A-Z0-9]+)\\/([^/]+)-[^/]+\\.git", "/git/$1/$2.git");
        // False positives: tests, exercise & solution repositories
        if (unscrubbed.contains("-tests.git") || unscrubbed.contains("-exercise.git") || unscrubbed.contains("-solution.git")) {
            return unscrubbed;
        }
        return scrubbed;
    }

    // Scrub user data from breadcrumbs
    private Breadcrumb scrubBreadcrumb(Breadcrumb crumb, Hint hint) {
        String message = crumb.getMessage();
        if (message != null) {
            crumb.setMessage(scrubStringMessage(message));
        }
        return crumb;
    }

    private SentryBaseEvent scrubData(SentryBaseEvent event) {
        // Handle fields specific to SentryEvent
        if (event instanceof SentryEvent errorEvent) {
            if (errorEvent.getMessage() != null) {
                // The actual string is wrapped inside a message object
                String message = errorEvent.getMessage().getMessage();
                if (message != null) {
                    errorEvent.getMessage().setMessage(scrubStringMessage(message));
                }

                String formattedMessage = errorEvent.getMessage().getFormatted();
                if (formattedMessage != null) {
                    errorEvent.getMessage().setFormatted(scrubStringMessage(formattedMessage));
                }
                if (errorEvent.getMessage().getParams() != null) {
                    List<String> filteredParams = errorEvent.getMessage().getParams().stream().map(this::scrubStringMessage).toList();
                    errorEvent.getMessage().setParams(filteredParams);
                }
            }

            if (errorEvent.getTransaction() != null) {
                errorEvent.setTransaction(scrubUrl(scrubStringMessage(errorEvent.getTransaction())));
            }

            if (errorEvent.getExceptions() != null) {
                for (SentryException ex : errorEvent.getExceptions()) {
                    String value = ex.getValue();
                    if (value != null) {
                        ex.setValue(scrubStringMessage(value));
                    }
                }
            }
        }

        // GetRequest can be called on any SentryBaseEvent, so we use common handling
        Request request = event.getRequest();
        if (request != null)

        {
            // Send no cookies
            request.setCookies("");

            // Scrub user data from URL
            String url = request.getUrl();
            if (url != null) {
                request.setUrl(scrubUrl(url));
            }

            if (request.getHeaders() != null) {
                request.setHeaders(request.getHeaders().entrySet().stream().filter((entry) -> !entry.getKey().toLowerCase().startsWith("x-artemis-client-"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
        }
        event.setUser(null); // Make sure to never send user data
        return event;
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
        if (env.contains("test") || env.contains("staging")) {
            return 1.0;
        }

        // Only "prod" get 0.05, all others (like local) are disabled
        return switch (env) {
            case "prod" -> 0.05;
            default -> 0.0;
        };
    }
}
