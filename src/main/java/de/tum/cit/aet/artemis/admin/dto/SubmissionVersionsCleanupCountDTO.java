package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(requiredProperties = { "submissionVersions" })
public record SubmissionVersionsCleanupCountDTO(int submissionVersions) {
}
