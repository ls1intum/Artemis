package de.tum.cit.aet.artemis.hyperion.config;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Lazy;

/** Core's scoped transport credentials and explicitly provisioned generation workers. */
@Lazy
@ConfigurationProperties("artemis.hyperion.workers")
public record HyperionWorkerProperties(String brokerUrl, String user, String password, List<String> ids, @DefaultValue("PT30S") Duration presenceTtl,
        @DefaultValue("PT45S") Duration leaseTtl) {

    private static final Pattern SSL_ENABLED = Pattern.compile(".*[?&;]sslEnabled=true(?:[&;].*)?$");

    private static final Pattern WORKER_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public HyperionWorkerProperties {
        if (brokerUrl == null || !brokerUrl.startsWith("tcp://") || !SSL_ENABLED.matcher(brokerUrl).matches() || brokerUrl.contains("trustAll=true")
                || brokerUrl.contains("verifyHost=false")) {
            throw new IllegalArgumentException("Configure a TLS Hyperion broker URL with certificate verification");
        }
        if (user == null || user.isBlank() || password == null || password.isBlank() || ids == null || ids.isEmpty() || ids.size() > 64
                || ids.stream().anyMatch(id -> id == null || !WORKER_ID.matcher(id).matches()) || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("Configure scoped Hyperion broker credentials and distinct worker IDs");
        }
        ids = List.copyOf(ids);
        if (presenceTtl == null || presenceTtl.compareTo(Duration.ofSeconds(10)) <= 0 || leaseTtl == null || leaseTtl.compareTo(presenceTtl) <= 0) {
            throw new IllegalArgumentException("Worker presence and ownership timeouts are invalid");
        }
    }
}
