package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository.TemplateParticipationFetchOptions;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.DynamicSpecificationRepository;
import de.tum.cit.aet.artemis.core.repository.base.FetchOptions;
import de.tum.cit.aet.artemis.exercise.domain.Submission_;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation_;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TemplateProgrammingExerciseParticipationRepository
        extends DynamicSpecificationRepository<TemplateProgrammingExerciseParticipation, Long, TemplateParticipationFetchOptions> {

    @Query("""
            SELECT p
            FROM TemplateProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH p.programmingExercise e
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<TemplateProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(long exerciseId);

    default TemplateProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId));
    }

    Optional<TemplateProgrammingExerciseParticipation> findByRepositoryUri(@Param("repositoryUri") String repositoryUri);

    default TemplateProgrammingExerciseParticipation findByRepositoryUriElseThrow(String repositoryUri) {
        return getValueElseThrow(findByRepositoryUri(repositoryUri));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndTestCasesAndSubmissionsByProgrammingExerciseId(long exerciseId);

    @NotNull
    default TemplateProgrammingExerciseParticipation findByExerciseIdElseThrow(final Specification<TemplateProgrammingExerciseParticipation> specification, long exerciseId) {
        final Specification<TemplateProgrammingExerciseParticipation> hasExerciseIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder
                .equal(root.get(TemplateProgrammingExerciseParticipation_.PROGRAMMING_EXERCISE).get(DomainObject_.ID), exerciseId);
        return getValueElseThrow(findOneBySpec(specification.and(hasExerciseIdSpec)));
    }

    /**
     * Finds a {@link TemplateProgrammingExerciseParticipation} by the ID of its associated programming exercise with optional dynamic fetching of associated entities.
     *
     * @param exerciseId   the ID of the associated programming exercise.
     * @param fetchOptions a collection of {@link TemplateParticipationFetchOptions} indicating which associated entities to fetch.
     * @return the {@link TemplateProgrammingExerciseParticipation} associated with the specified programming exercise ID and the associated entities fetched according to the
     *         provided options.
     * @throws EntityNotFoundException if the template programming exercise participation with the specified exercise ID does not exist.
     */
    @NotNull
    default TemplateProgrammingExerciseParticipation findByExerciseIdWithDynamicFetchElseThrow(long exerciseId, Collection<TemplateParticipationFetchOptions> fetchOptions)
            throws EntityNotFoundException {
        var specification = getDynamicSpecification(fetchOptions);
        return findByExerciseIdElseThrow(specification, exerciseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerSubmissionsByProgrammingExerciseId(long exerciseId);

    Optional<TemplateProgrammingExerciseParticipation> findByProgrammingExerciseId(long programmingExerciseId);

    default TemplateProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(long programmingExerciseId) {
        return getValueElseThrow(findByProgrammingExerciseId(programmingExerciseId));
    }

    /**
     * Fetch options for the {@link TemplateProgrammingExerciseParticipation} entity.
     * Each option specifies an entity or a collection of entities to fetch eagerly when using a dynamic fetching query.
     */
    enum TemplateParticipationFetchOptions implements FetchOptions {

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
}
