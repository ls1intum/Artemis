package de.tum.in.www1.artemis.service.connectors.localci.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultBuildJob(long resultId, long buildJobId) {
}
