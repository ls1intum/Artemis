package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for the programming exercise reset options.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseResetOptionsDTO {

    private boolean deleteBuildPlans;

    private boolean deleteStudentRepositories;

    private boolean deleteStudentParticipationsSubmissionsAndResults;

    private boolean recreateBuildPlans;

    public boolean isDeleteBuildPlans() {
        return deleteBuildPlans;
    }

    public void setDeleteBuildPlans(boolean deleteBuildPlans) {
        this.deleteBuildPlans = deleteBuildPlans;
    }

    public boolean isDeleteRepositories() {
        return deleteStudentRepositories;
    }

    public void setDeleteStudentRepositories(boolean deleteStudentRepositories) {
        this.deleteStudentRepositories = deleteStudentRepositories;
    }

    public boolean isDeleteStudentParticipationsSubmissionsAndResults() {
        return deleteStudentParticipationsSubmissionsAndResults;
    }

    public void setDeleteStudentParticipationsSubmissionsAndResults(boolean deleteStudentParticipationsSubmissionsAndResults) {
        this.deleteStudentParticipationsSubmissionsAndResults = deleteStudentParticipationsSubmissionsAndResults;
    }

    public boolean isRecreateBuildPlans() {
        return recreateBuildPlans;
    }

    public void setRecreateBuildPlans(boolean recreateBuildPlans) {
        this.recreateBuildPlans = recreateBuildPlans;
    }
}
