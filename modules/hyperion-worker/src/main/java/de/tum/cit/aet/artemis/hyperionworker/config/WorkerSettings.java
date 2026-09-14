package de.tum.cit.aet.artemis.hyperionworker.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Operator-owned worker limits and image. No job or model can widen these values. */
@ConfigurationProperties("artemis.hyperion.worker")
public record WorkerSettings(String id, String image, @DefaultValue("runc") String runtime, @DefaultValue("2147483648") long memoryBytes, @DefaultValue("200000") long cpuQuota,
        @DefaultValue("256") long pids, @DefaultValue("PT10S") Duration heartbeatInterval, @DefaultValue("PT45S") Duration connectionGrace,
        @DefaultValue("PT2M") Duration shutdownTimeout, @DefaultValue("1") int maxConcurrentGenerations) {

    public WorkerSettings(String id, String image, String runtime, long memoryBytes, long cpuQuota, long pids, Duration heartbeatInterval, Duration connectionGrace,
            Duration shutdownTimeout) {
        this(id, image, runtime, memoryBytes, cpuQuota, pids, heartbeatInterval, connectionGrace, shutdownTimeout, 1);
    }

    @ConstructorBinding
    public WorkerSettings {
        if (maxConcurrentGenerations < 1 || maxConcurrentGenerations > 16) {
            throw new IllegalArgumentException("Configure between one and sixteen generation slots");
        }
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Configure a unique destination-safe worker id");
        }
        if (image == null || !image.matches("(?:[^\\s]+@)?sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("The worker image must be pinned by SHA-256 digest");
        }
        if (runtime == null || runtime.isBlank() || memoryBytes <= 0 || cpuQuota <= 0 || pids <= 0) {
            throw new IllegalArgumentException("Sandbox runtime and positive resource limits are required");
        }
        if (heartbeatInterval == null || !heartbeatInterval.isPositive() || connectionGrace == null || connectionGrace.compareTo(heartbeatInterval) <= 0 || shutdownTimeout == null
                || !shutdownTimeout.isPositive()) {
            throw new IllegalArgumentException("Worker heartbeat, connection grace and drain limits are invalid");
        }
    }
}
