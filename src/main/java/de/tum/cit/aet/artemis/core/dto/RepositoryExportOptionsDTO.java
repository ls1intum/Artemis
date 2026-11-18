package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for the repository export options.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryExportOptionsDTO(boolean exportAllParticipants, boolean filterLateSubmissions, boolean filterLateSubmissionsIndividualDueDate,
        ZonedDateTime filterLateSubmissionsDate, boolean excludePracticeSubmissions, boolean addParticipantName, boolean combineStudentCommits, boolean anonymizeRepository,
        boolean normalizeCodeStyle) {

    public RepositoryExportOptionsDTO() {
        this(false, false, false, null, false, false, false, false, false);
    }

    public RepositoryExportOptionsDTO copyWith(boolean filterLateSubmissionsIndividualDueDate, ZonedDateTime filterLateSubmissionsDate) {
        return new RepositoryExportOptionsDTO(exportAllParticipants, filterLateSubmissions, filterLateSubmissionsIndividualDueDate, filterLateSubmissionsDate,
                excludePracticeSubmissions, addParticipantName, combineStudentCommits, anonymizeRepository, normalizeCodeStyle);
    }

    public RepositoryExportOptionsDTO copyWithAnonymizeRepository(boolean anonymizeRepository) {
        return new RepositoryExportOptionsDTO(exportAllParticipants, filterLateSubmissions, filterLateSubmissionsIndividualDueDate, filterLateSubmissionsDate,
                excludePracticeSubmissions, addParticipantName, combineStudentCommits, anonymizeRepository, normalizeCodeStyle);
    }
}
