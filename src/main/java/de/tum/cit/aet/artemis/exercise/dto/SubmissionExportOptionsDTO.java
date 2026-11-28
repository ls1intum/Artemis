package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * This is a dto for the repository export options.
 *
 * @param exportAllParticipants     whether to export all participants
 * @param filterLateSubmissions     whether to filter late submissions
 * @param filterLateSubmissionsDate the date to filter late submissions
 * @param participantIdentifierList the list of participant identifiers
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionExportOptionsDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exportAllParticipants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean filterLateSubmissions, ZonedDateTime filterLateSubmissionsDate, String participantIdentifierList) {

    /**
     * Creates a new SubmissionExportOptionsDTO with default values.
     */
    public SubmissionExportOptionsDTO() {
        this(false, false, null, null);
    }

    /**
     * Creates options for exporting all participants.
     *
     * @return a new SubmissionExportOptionsDTO configured to export all participants
     */
    public static SubmissionExportOptionsDTO exportAll() {
        return new SubmissionExportOptionsDTO(true, false, null, null);
    }
}
