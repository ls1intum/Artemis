package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PyrisHealthStatusDTO(boolean isHealthy, Map<String, ModuleStatusDTO> modules) {

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
