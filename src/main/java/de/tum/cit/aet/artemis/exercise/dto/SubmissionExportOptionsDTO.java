package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for the repository export options.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SubmissionExportOptionsDTO {

    private boolean exportAllParticipants;

    private boolean filterLateSubmissions;

    private ZonedDateTime filterLateSubmissionsDate;

    private String participantIdentifierList;

    public boolean isExportAllParticipants() {
        return exportAllParticipants;
    }

    public void setExportAllParticipants(boolean exportAllParticipants) {
        this.exportAllParticipants = exportAllParticipants;
    }

    public boolean isFilterLateSubmissions() {
        return filterLateSubmissions;
    }

    public void setFilterLateSubmissions(boolean filterLateSubmissions) {
        this.filterLateSubmissions = filterLateSubmissions;
    }

    public ZonedDateTime getFilterLateSubmissionsDate() {
        return filterLateSubmissionsDate;
    }

    public void setFilterLateSubmissionsDate(ZonedDateTime filterLateSubmissionsDate) {
        this.filterLateSubmissionsDate = filterLateSubmissionsDate;
    }

    public String getParticipantIdentifierList() {
        return participantIdentifierList;
    }

    public void setParticipantIdentifierList(String participantIdentifierList) {
        this.participantIdentifierList = participantIdentifierList;
    }
}
