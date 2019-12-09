package de.tum.in.www1.artemis.domain.participation;

import java.net.URL;
import java.util.Set;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    String getRepositoryUrl();

    void setRepositoryUrl(String repositoryUrl);

    String getBuildPlanId();

    void setBuildPlanId(String buildPlanId);

    URL getRepositoryUrlAsUrl();

    ProgrammingExercise getProgrammingExercise();

    void setProgrammingExercise(ProgrammingExercise programmingExercise);

    Set<Result> getResults();
}
