package de.tum.cit.aet.artemis.aiworker.config;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Lazy;

/** Explicitly provisioned workers and their presence and lease timeouts. */
@Lazy
@ConfigurationProperties("artemis.aiworker")
public record AiWorkerProperties(List<String> ids, @DefaultValue("PT30S") Duration presenceTtl, @DefaultValue("PT45S") Duration leaseTtl) {

    private static final Pattern WORKER_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public AiWorkerProperties {
        if (ids == null || ids.isEmpty() || ids.size() > 64 || ids.stream().anyMatch(id -> id == null || !WORKER_ID.matcher(id).matches())
                || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("Configure distinct AI worker IDs");
        }
        ids = List.copyOf(ids);
        if (presenceTtl == null || presenceTtl.compareTo(Duration.ofSeconds(10)) <= 0 || leaseTtl == null || leaseTtl.compareTo(presenceTtl) <= 0) {
            throw new IllegalArgumentException("Worker presence and ownership timeouts are invalid");
        }
    }
}
