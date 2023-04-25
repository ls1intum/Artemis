package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for the programming exercise reset options.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseResetOptionsDTO {

    private boolean deleteBuildPlans;

    private boolean deleteRepositories;

    private boolean deleteParticipationsSubmissionsAndResults;

    private boolean recreateBuildPlans;

    public boolean isDeleteBuildPlans() {
        return deleteBuildPlans;
    }

    public void setDeleteBuildPlans(boolean deleteBuildPlans) {
        this.deleteBuildPlans = deleteBuildPlans;
    }

    public boolean isDeleteRepositories() {
        return deleteRepositories;
    }

    public void setDeleteRepositories(boolean deleteRepositories) {
        this.deleteRepositories = deleteRepositories;
    }

    public boolean isDeleteParticipationsSubmissionsAndResults() {
        return deleteParticipationsSubmissionsAndResults;
    }

    public void setDeleteParticipationsSubmissionsAndResults(boolean deleteParticipationsSubmissionsAndResults) {
        this.deleteParticipationsSubmissionsAndResults = deleteParticipationsSubmissionsAndResults;
    }

    public boolean isRecreateBuildPlans() {
        return recreateBuildPlans;
    }

    public void setRecreateBuildPlans(boolean recreateBuildPlans) {
        this.recreateBuildPlans = recreateBuildPlans;
    }
}
