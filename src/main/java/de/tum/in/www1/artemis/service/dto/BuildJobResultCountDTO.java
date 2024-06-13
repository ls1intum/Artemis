package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobResultCountDTO(BuildStatus status, long count) {
}
