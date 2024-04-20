package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DockerImageBuild(String dockerImage, ZonedDateTime lastBuildCompletionDate) {
}
