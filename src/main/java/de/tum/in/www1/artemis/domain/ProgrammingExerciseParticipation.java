package de.tum.in.www1.artemis.domain;

import java.net.URL;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    String getRepositoryUrl();

    void setRepositoryUrl(String repositoryUrl);

    String getBuildPlanId();

    void setBuildPlanId(String buildPlanId);

    URL getRepositoryUrlAsUrl();

    ProgrammingExercise getProgrammingExercise();

    void setProgrammingExercise(ProgrammingExercise programmingExercise);
}
