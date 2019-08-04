package de.tum.in.www1.artemis.domain;

import java.net.URL;

public interface ProgrammingExerciseParticipation {

    public Long getId();

    public String getRepositoryUrl();

    public void setRepositoryUrl(String repositoryUrl);

    public String getBuildPlanId();

    public void setBuildPlanId(String buildPlanId);

    public URL getRepositoryUrlAsUrl();

    public ProgrammingExercise getProgrammingExercise();

    public void setProgrammingExercise(ProgrammingExercise programmingExercise);

    public void addSubmissions(ProgrammingSubmission submission);
}
