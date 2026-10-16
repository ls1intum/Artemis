package de.tum.cit.aet.artemis.aiworker.config;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

/** Operator-owned worker limits and image. No job or model can widen these values. */
@ConfigurationProperties("artemis.aiworker")
public record WorkerSettings(String id, String image, @DefaultValue("runc") String runtime, @DefaultValue("2147483648") long memoryBytes, @DefaultValue("200000") long cpuQuota,
        @DefaultValue("256") long pids, @DefaultValue("PT10S") Duration heartbeatInterval, @DefaultValue("PT45S") Duration connectionGrace,
        @DefaultValue("PT2M") Duration shutdownTimeout, @DefaultValue("1") int maxConcurrentExecutions, String workload, String profile, @DefaultValue("1") int workloadVersion) {

    private static final Pattern WORKER_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private static final Pattern IMAGE_PATTERN = Pattern.compile("(?:[^\\s]+@)?sha256:[a-f0-9]{64}");

    public WorkerSettings(String id, String image, String runtime, long memoryBytes, long cpuQuota, long pids, Duration heartbeatInterval, Duration connectionGrace,
            Duration shutdownTimeout, int maxConcurrentExecutions, String workload, String profile) {
        this(id, image, runtime, memoryBytes, cpuQuota, pids, heartbeatInterval, connectionGrace, shutdownTimeout, maxConcurrentExecutions, workload, profile, 1);
    }

    @ConstructorBinding
    public WorkerSettings {
        new WorkloadCapabilityDTO(workload, workloadVersion, profile);
        if (maxConcurrentExecutions < 1 || maxConcurrentExecutions > WorkerCapacityDTO.MAX_SLOTS) {
            throw new IllegalArgumentException("Configure between one and " + WorkerCapacityDTO.MAX_SLOTS + " execution slots");
        }
        if (id == null || !WORKER_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Configure a unique destination-safe worker id");
        }
        if (image == null || !IMAGE_PATTERN.matcher(image).matches()) {
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

    public WorkloadCapabilityDTO capability() {
        return new WorkloadCapabilityDTO(workload, workloadVersion, profile);
    }
}
