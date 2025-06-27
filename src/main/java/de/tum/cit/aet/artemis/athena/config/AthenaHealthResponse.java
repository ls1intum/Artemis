package de.tum.cit.aet.artemis.athena.config;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AthenaHealthResponse(String status, Map<String, ModuleHealth> modules) {

    public record ModuleHealth(String url, String type, boolean healthy, boolean supportsEvaluation, boolean supportsNonGradedFeedbackRequests,
            boolean supportsGradedFeedbackRequests) {
    }
}
