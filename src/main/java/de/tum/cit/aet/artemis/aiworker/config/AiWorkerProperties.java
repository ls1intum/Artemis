package de.tum.cit.aet.artemis.aiworker.config;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Lazy;

/** Core's scoped transport credentials and explicitly provisioned AI workers. */
@Lazy
@ConfigurationProperties("artemis.aiworker")
public record AiWorkerProperties(String brokerUrl, String user, String password, List<String> ids, @DefaultValue("PT30S") Duration presenceTtl,
        @DefaultValue("PT45S") Duration leaseTtl) {

    private static final Pattern WORKER_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public AiWorkerProperties {
        if (!verifiedBrokerUrl(brokerUrl)) {
            throw new IllegalArgumentException("Configure a TLS AI worker broker URL with certificate verification");
        }
        if (user == null || user.isBlank() || password == null || password.isBlank() || ids == null || ids.isEmpty() || ids.size() > 64
                || ids.stream().anyMatch(id -> id == null || !WORKER_ID.matcher(id).matches()) || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("Configure scoped AI worker broker credentials and distinct worker IDs");
        }
        ids = List.copyOf(ids);
        if (presenceTtl == null || presenceTtl.compareTo(Duration.ofSeconds(10)) <= 0 || leaseTtl == null || leaseTtl.compareTo(presenceTtl) <= 0) {
            throw new IllegalArgumentException("Worker presence and ownership timeouts are invalid");
        }
    }

    private static boolean verifiedBrokerUrl(String url) {
        if (url == null) {
            return false;
        }
        URI broker = URI.create(url);
        if (!"tcp".equals(broker.getScheme()) || broker.getHost() == null || broker.getUserInfo() != null || broker.getFragment() != null || broker.getRawQuery() == null) {
            return false;
        }
        Map<String, String> options = new HashMap<>();
        for (String option : broker.getRawQuery().split("[&;]", -1)) {
            String[] pair = option.split("=", 2);
            // ActiveMQ decodes query keys. Reject encoded keys so its interpretation cannot add or override a TLS option after this check.
            if (pair.length != 2 || pair[0].isBlank() || pair[0].indexOf('%') >= 0 || pair[0].indexOf('+') >= 0 || options.putIfAbsent(pair[0], pair[1]) != null) {
                return false;
            }
        }
        return "true".equals(options.get("sslEnabled")) && "false".equals(options.getOrDefault("trustAll", "false")) && "true".equals(options.getOrDefault("verifyHost", "true"));
    }
}
