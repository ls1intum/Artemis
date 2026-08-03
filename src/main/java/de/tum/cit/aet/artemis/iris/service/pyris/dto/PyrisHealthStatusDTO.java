package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PyrisHealthStatusDTO(boolean isHealthy, Map<String, ModuleStatusDTO> modules, @Nullable String instanceId) {

    /**
     * Backwards-compatible constructor for callers that predate the instance id (a stable per-process id Iris reports so
     * Artemis can detect a genuine restart).
     *
     * @param isHealthy whether Iris is healthy overall
     * @param modules   the per-module statuses
     */
    public PyrisHealthStatusDTO(boolean isHealthy, Map<String, ModuleStatusDTO> modules) {
        this(isHealthy, modules, null);
    }

    public enum ServiceStatus {
        UP, WARN, DEGRADED, DOWN
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModuleStatusDTO(ServiceStatus status, String error, String metaData) {

        public boolean isUp() {
            return status != ServiceStatus.DOWN;
        }
    }
}
