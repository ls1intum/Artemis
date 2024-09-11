package de.tum.cit.aet.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for the repository export options.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// TODO: convert into a Record
public class RepositoryExportOptionsDTO {

    private boolean exportAllParticipants;

    private boolean filterLateSubmissions;

    private boolean filterLateSubmissionsIndividualDueDate;

    private ZonedDateTime filterLateSubmissionsDate;

    private boolean excludePracticeSubmissions;

    private boolean addParticipantName;

    private boolean combineStudentCommits;

    private boolean anonymizeRepository;

    private boolean normalizeCodeStyle;

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

    public boolean isFilterLateSubmissionsIndividualDueDate() {
        return filterLateSubmissionsIndividualDueDate;
    }

    public void setFilterLateSubmissionsIndividualDueDate(boolean filterLateSubmissionsIndividualDueDate) {
        this.filterLateSubmissionsIndividualDueDate = filterLateSubmissionsIndividualDueDate;
    }

    public ZonedDateTime getFilterLateSubmissionsDate() {
        return filterLateSubmissionsDate;
    }

    public void setFilterLateSubmissionsDate(ZonedDateTime filterLateSubmissionsDate) {
        this.filterLateSubmissionsDate = filterLateSubmissionsDate;
    }

    public boolean isExcludePracticeSubmissions() {
        return excludePracticeSubmissions;
    }

    public void setExcludePracticeSubmissions(boolean excludePracticeSubmissions) {
        this.excludePracticeSubmissions = excludePracticeSubmissions;
    }

    public boolean isAddParticipantName() {
        return addParticipantName;
    }

    public void setAddParticipantName(boolean addParticipantName) {
        this.addParticipantName = addParticipantName;
    }

    public boolean isCombineStudentCommits() {
        return combineStudentCommits;
    }

    public void setCombineStudentCommits(boolean combineStudentCommits) {
        this.combineStudentCommits = combineStudentCommits;
    }

    public boolean isAnonymizeRepository() {
        return anonymizeRepository;
    }

    public void setAnonymizeRepository(boolean anonymizeRepository) {
        this.anonymizeRepository = anonymizeRepository;
    }

    public boolean isNormalizeCodeStyle() {
        return normalizeCodeStyle;
    }

    public void setNormalizeCodeStyle(boolean normalizeCodeStyle) {
        this.normalizeCodeStyle = normalizeCodeStyle;
    }
}
