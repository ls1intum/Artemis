package de.tum.in.www1.artemis.domain.participation;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    String getRepositoryUrl();

    void setRepositoryUrl(String repositoryUrl);

    String getBuildPlanId();

    void setBuildPlanId(String buildPlanId);

    ProgrammingExercise getProgrammingExercise();

    void setProgrammingExercise(ProgrammingExercise programmingExercise);

    Set<Result> getResults();

    /**
     * @return the repository url of the programming exercise participation wrapped in an object
     */
    @JsonIgnore
    default VcsRepositoryUrl getVcsRepositoryUrl() {
        var repoUrl = getRepositoryUrl();
        if (repoUrl == null) {
            return null;
        }

        try {
            return new VcsRepositoryUrl(repoUrl);
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Check if the participation is locked.
     * This is the case when the participation is a ProgrammingExerciseStudentParticipation, the buildAndTestAfterDueDate of the exercise is set and the due date has passed,
     * or if manual correction is involved and the due date has passed.
     *
     * Locked means that the student can't make any changes to their repository anymore. While we can control this easily in the remote VCS, we need to check this manually for the local repository on the Artemis server.
     *
     * @return true if repository is locked, false if not.
     */
    @JsonIgnore
    default boolean isLocked() {
        if (this instanceof ProgrammingExerciseStudentParticipation) {
            ProgrammingExercise programmingExercise = getProgrammingExercise();

            final ZonedDateTime now = ZonedDateTime.now();
            boolean isAfterDueDate = (getIndividualDueDate() != null && now.isAfter(getIndividualDueDate()))
                    || (programmingExercise.getDueDate() != null && now.isAfter(programmingExercise.getDueDate()));

            // Editing is allowed if build and test after due date is not set and no manual correction is involved
            // (this should match CodeEditorStudentContainerComponent.repositoryIsLocked on the client-side)
            boolean isEditingAfterDueAllowed = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null
                    && programmingExercise.getAssessmentType() != AssessmentType.MANUAL && programmingExercise.getAssessmentType() != AssessmentType.SEMI_AUTOMATIC
                    && !programmingExercise.areManualResultsAllowed();
            return isAfterDueDate && !isEditingAfterDueAllowed;
        }
        return false;
    }
}
