package de.tum.cit.aet.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.BuildStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobResultCountDTO(BuildStatus status, long count) {
}
