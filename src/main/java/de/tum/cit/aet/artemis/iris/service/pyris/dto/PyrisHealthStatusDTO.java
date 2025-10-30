package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PyrisHealthStatusDTO(Map<String, ModuleStatusDTO> modules) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModuleStatusDTO(Boolean healthy, String url, String error) {

        public boolean isHealthy() {
            return Boolean.TRUE.equals(healthy);
        }
    }
}
