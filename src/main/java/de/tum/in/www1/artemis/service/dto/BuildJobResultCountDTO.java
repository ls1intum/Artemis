package de.tum.in.www1.artemis.service.dto;

import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;

public record BuildJobResultCountDTO(BuildStatus status, long count) {
}
