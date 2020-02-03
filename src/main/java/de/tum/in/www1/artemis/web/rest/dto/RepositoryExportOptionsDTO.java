package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

/**
 * This is a dto for the repository export options.
 */
public class RepositoryExportOptionsDTO {

    private boolean exportAllStudents;

    private boolean filterLateSubmissions;

    private ZonedDateTime filterLateSubmissionsDate;

    private boolean addStudentName;

    private boolean combineStudentCommits;

    private boolean normalizeCodeStyle;

    public boolean isExportAllStudents() {
        return exportAllStudents;
    }

    public void setExportAllStudents(boolean exportAllStudents) {
        this.exportAllStudents = exportAllStudents;
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

    public boolean isAddStudentName() {
        return addStudentName;
    }

    public void setAddStudentName(boolean addStudentName) {
        this.addStudentName = addStudentName;
    }

    public boolean isCombineStudentCommits() {
        return combineStudentCommits;
    }

    public void setCombineStudentCommits(boolean combineStudentCommits) {
        this.combineStudentCommits = combineStudentCommits;
    }

    public boolean isNormalizeCodeStyle() {
        return normalizeCodeStyle;
    }

    public void setNormalizeCodeStyle(boolean normalizeCodeStyle) {
        this.normalizeCodeStyle = normalizeCodeStyle;
    }
}
