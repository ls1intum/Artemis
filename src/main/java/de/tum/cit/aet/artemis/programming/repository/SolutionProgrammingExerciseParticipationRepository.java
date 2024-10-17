package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository.SolutionParticipationFetchOptions;
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
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation_;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation_;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SolutionProgrammingExerciseParticipationRepository
        extends DynamicSpecificationRepository<SolutionProgrammingExerciseParticipation, Long, SolutionParticipationFetchOptions> {

    @Query("""
            SELECT p
            FROM SolutionProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results
                LEFT JOIN FETCH p.programmingExercise e
                LEFT JOIN FETCH e.templateParticipation
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    Optional<SolutionProgrammingExerciseParticipation> findByRepositoryUri(@Param("repositoryUri") String repositoryUri);

    default SolutionProgrammingExerciseParticipation findByRepositoryUriElseThrow(String repositoryUri) {
        return getValueElseThrow(findByRepositoryUri(repositoryUri));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions", "submissions.results" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(long exerciseId);

    default SolutionProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndTestCasesAndSubmissionsByProgrammingExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerSubmissionsByProgrammingExerciseId(long exerciseId);

    @NotNull
    default SolutionProgrammingExerciseParticipation findByExerciseIdElseThrow(final Specification<SolutionProgrammingExerciseParticipation> specification, long exerciseId) {
        final Specification<SolutionProgrammingExerciseParticipation> hasExerciseIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder
                .equal(root.get(TemplateProgrammingExerciseParticipation_.PROGRAMMING_EXERCISE).get(DomainObject_.ID), exerciseId);
        return getValueElseThrow(findOneBySpec(specification.and(hasExerciseIdSpec)));
    }

    /**
     * Finds a {@link SolutionProgrammingExerciseParticipation} by the ID of its associated programming exercise with optional dynamic fetching of associated entities.
     *
     * @param exerciseId   the ID of the associated programming exercise.
     * @param fetchOptions a collection of {@link SolutionParticipationFetchOptions} indicating which associated entities to fetch.
     * @return the {@link SolutionProgrammingExerciseParticipation} associated with the specified programming exercise ID and the associated entities fetched according to the
     *         provided options.
     * @throws EntityNotFoundException if the solution programming exercise participation with the specified exercise ID does not exist.
     */
    @NotNull
    default SolutionProgrammingExerciseParticipation findByExerciseIdWithDynamicFetchElseThrow(long exerciseId, Collection<SolutionParticipationFetchOptions> fetchOptions)
            throws EntityNotFoundException {
        var specification = getDynamicSpecification(fetchOptions);
        return findByExerciseIdElseThrow(specification, exerciseId);
    }

    Optional<SolutionProgrammingExerciseParticipation> findByProgrammingExerciseId(long programmingExerciseId);

    default SolutionProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(long programmingExerciseId) {
        return getValueElseThrow(findByProgrammingExerciseId(programmingExerciseId));
    }

    /**
     * Fetch options for the {@link SolutionProgrammingExerciseParticipation} entity.
     * Each option specifies an entity or a collection of entities to fetch eagerly when using a dynamic fetching query.
     */
    enum SolutionParticipationFetchOptions implements FetchOptions {

        // @formatter:off
        Submissions(SolutionProgrammingExerciseParticipation_.SUBMISSIONS),
        SubmissionsAndResults(SolutionProgrammingExerciseParticipation_.SUBMISSIONS, Submission_.RESULTS);
        // @formatter:on

        private final String fetchPath;

        SolutionParticipationFetchOptions(String... fetchPath) {
            this.fetchPath = String.join(".", fetchPath);
        }

        public String getFetchPath() {
            return fetchPath;
        }
    }
}
