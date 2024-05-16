package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation_;

public enum TemplateParticipationFetchOptions {

    // @formatter:off
    Submissions(TemplateProgrammingExerciseParticipation_.SUBMISSIONS),
    SubmissionsAndResults("submissions.results");
    // @formatter:on

    private final String fetchPath;

    TemplateParticipationFetchOptions(String fetchPath) {
        this.fetchPath = fetchPath;
    }

    public String getFetchPath() {
        return fetchPath;
    }
}
