package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionVersionsCleanupCountDTO(int submissionVersions) {
}
