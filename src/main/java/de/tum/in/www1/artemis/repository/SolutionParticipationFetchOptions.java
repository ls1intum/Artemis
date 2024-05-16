package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation_;

public enum SolutionParticipationFetchOptions {

    // @formatter:off
    Submissions(SolutionProgrammingExerciseParticipation_.SUBMISSIONS),
    SubmissionsAndResults("submissions.results");
    // @formatter:on

    private final String fetchPath;

    SolutionParticipationFetchOptions(String fetchPath) {
        this.fetchPath = fetchPath;
    }

    public String getFetchPath() {
        return fetchPath;
    }
}
