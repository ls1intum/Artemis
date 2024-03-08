package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Deprecated(forRemoval = true) // will be removed in 7.0.0
public record BambooBuildLogDTO(ZonedDateTime date, String log, String unstyledLog) {
}
