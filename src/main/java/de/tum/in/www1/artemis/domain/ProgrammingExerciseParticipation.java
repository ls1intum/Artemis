package de.tum.in.www1.artemis.domain;

import java.net.URL;
import java.util.Set;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    String getRepositoryUrl();

    void setRepositoryUrl(String repositoryUrl);

    String getBuildPlanId();

    void setBuildPlanId(String buildPlanId);

    URL getRepositoryUrlAsUrl();

    ProgrammingExercise getProgrammingExercise();

    void setProgrammingExercise(ProgrammingExercise programmingExercise);

    void addSubmissions(ProgrammingSubmission submission);

    Set<Result> getResults();
}
