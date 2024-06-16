package de.tum.in.www1.artemis.repository.fetchOptions;

import de.tum.in.www1.artemis.domain.Submission_;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation_;

/**
 * Fetch options for the {@link de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation} entity.
 * Each option specifies an entity or a collection of entities to fetch eagerly when using a dynamic fetching query.
 */
public enum TemplateParticipationFetchOptions implements FetchOptions {

    // @formatter:off
    Submissions(TemplateProgrammingExerciseParticipation_.SUBMISSIONS),
    SubmissionsAndResults(TemplateProgrammingExerciseParticipation_.SUBMISSIONS, Submission_.RESULTS);
    // @formatter:on

    private final String fetchPath;

    TemplateParticipationFetchOptions(String... fetchPath) {
        this.fetchPath = String.join(".", fetchPath);
    }

    public String getFetchPath() {
        return fetchPath;
    }
}
