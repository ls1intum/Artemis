package de.tum.cit.aet.artemis.buildagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildJobResultCountDTO(BuildStatus status, long count) {
}
