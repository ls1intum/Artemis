package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Service determining the health of the Hermes push notification relay.
 * <p>
 * The verdict is based on the dedicated {@code /api/health} endpoint, which is the only signal that reflects
 * the actual state of the relay (its Firebase / APNs connectivity). Hermes is reported as {@code DOWN} when
 * that endpoint is unreachable, times out, returns a non-2xx status, or reports a disconnected push provider
 * ({@code isApnsConnected} or {@code isFirebaseConnected} is {@code false}). The latter matters because such a
 * relay still answers 2xx while silently dropping every notification &mdash; e.g. after a missing or expired
 * APNs certificate on the Hermes host.
 * </p>
 * <p>
 * We deliberately do <b>not</b> fall back to pinging the base URL: it is served by the reverse proxy (nginx)
 * and returns 2xx even when the relay itself is down, which would mask an outage as a healthy {@code UP}
 * (a false positive).
 * </p>
 * <p>
 * Detection of a disconnected provider relies on Hermes reporting the condition truthfully via these flags.
 * A relay that reports {@code isApnsConnected=true} while actually unable to deliver cannot be detected from
 * the outside; closing that gap requires {@code /api/health} on Hermes to genuinely validate the certificate
 * and provider connection.
 * </p>
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class HermesHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HermesHealthIndicator.class);

    private static final String URL_KEY = "url";

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.push-notification-relay:https://hermes-staging.artemis.cit.tum.de}")
    private String hermesUrl;

    // @Lazy: the RestTemplate is only used in health() (request-time). Keeps the REST template configuration out of eager startup.
    public HermesHealthIndicator(@Lazy @Qualifier("shortTimeoutHermesRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    /**
     * Ping Hermes at {@code /api/health} and report whether the push notification relay can deliver.
     *
     * @return the connector health; {@code UP} only when {@code /api/health} responds successfully and reports
     *         every push provider as connected
     */
    @Override
    public Health health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(URL_KEY, hermesUrl);
        try {
            ResponseEntity<String> response = shortTimeoutRestTemplate.getForEntity(hermesUrl + "/api/health", String.class);
            boolean up = response.getStatusCode().is2xxSuccessful();
            HermesHealthReport report = parseReport(response.getBody());
            if (report != null) {
                addReportDetails(additionalInfo, report);
                // A reachable relay that reports a disconnected provider returns 2xx but silently drops
                // notifications (e.g. a missing/expired APNs certificate on the Hermes host), so treat it as DOWN.
                up = up && isConnected(report.isApnsConnected()) && isConnected(report.isFirebaseConnected());
            }
            return new ConnectorHealth(up, additionalInfo).asActuatorHealth();
        }
        catch (RestClientException error) {
            log.warn("Hermes push notification relay health check failed for {}/api/health: {}", hermesUrl, error.getMessage());
            additionalInfo.put("error", error.getMessage());
            return new ConnectorHealth(false, additionalInfo, error).asActuatorHealth();
        }
    }

    /**
     * A missing flag is not penalized (the relay may not report it); only an explicit {@code false} marks the
     * provider as disconnected.
     *
     * @param connected the reported connectivity flag, may be {@code null} when not present in the response
     * @return {@code true} unless the flag is explicitly {@code false}
     */
    private static boolean isConnected(Boolean connected) {
        return connected == null || connected;
    }

    /**
     * Parses the {@code /api/health} response body into a {@link HermesHealthReport}, tolerating unknown fields.
     * A body that cannot be parsed (e.g. because the relay added or renamed fields) yields {@code null} rather
     * than failing the health check: reachability of {@code /api/health} already contributed to the verdict.
     *
     * @param body the raw response body from {@code /api/health}, may be {@code null} or blank
     * @return the parsed report, or {@code null} if the body is empty or unparseable
     */
    private HermesHealthReport parseReport(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, HermesHealthReport.class);
        }
        catch (JsonProcessingException e) {
            log.warn("Could not parse Hermes health report from {}/api/health: {}", hermesUrl, e.getMessage());
            return null;
        }
    }

    private static void addReportDetails(Map<String, Object> additionalInfo, HermesHealthReport report) {
        if (report.isFirebaseConnected() != null) {
            additionalInfo.put("firebase_up", report.isFirebaseConnected() ? "up" : "down");
        }
        if (report.isApnsConnected() != null) {
            additionalInfo.put("apns_up", report.isApnsConnected() ? "up" : "down");
        }
        if (report.versionNumber() != null) {
            additionalInfo.put("version", report.versionNumber());
        }
    }

    private record HermesHealthReport(Boolean isApnsConnected, Boolean isFirebaseConnected, String versionNumber) {
    }
}
