package de.tum.in.www1.artemis.domain.participation;

import java.net.MalformedURLException;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

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
}
