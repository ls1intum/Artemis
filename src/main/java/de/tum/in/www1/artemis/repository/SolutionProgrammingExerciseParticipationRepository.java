package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.DomainObject_;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation_;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SolutionProgrammingExerciseParticipationRepository
        extends JpaRepository<SolutionProgrammingExerciseParticipation, Long>, JpaSpecificationExecutor<SolutionProgrammingExerciseParticipation> {

    @Query("""
            SELECT p
            FROM SolutionProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results
                LEFT JOIN FETCH p.programmingExercise e
                LEFT JOIN FETCH e.templateParticipation
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions", "submissions.results" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(long exerciseId);

    default SolutionProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(long exerciseId) {
        return findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).orElseThrow(() -> new EntityNotFoundException("ProgrammingExerciseParticipation", exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndTestCasesAndSubmissionsByProgrammingExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerSubmissionsByProgrammingExerciseId(long exerciseId);

    @NotNull
    default SolutionProgrammingExerciseParticipation findByExerciseIdElseThrow(final Specification<SolutionProgrammingExerciseParticipation> specification, long exerciseId) {
        final Specification<SolutionProgrammingExerciseParticipation> hasExerciseIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder
                .equal(root.get(TemplateProgrammingExerciseParticipation_.PROGRAMMING_EXERCISE).get(DomainObject_.ID), exerciseId);
        return findOne(specification.and(hasExerciseIdSpec)).orElseThrow(() -> new EntityNotFoundException("Template Programming Exercise Participation --> Exercise", exerciseId));
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

        final Specification<SolutionProgrammingExerciseParticipation> specification = (root, query, criteriaBuilder) -> {
            for (var option : fetchOptions) {
                if (option == SolutionParticipationFetchOptions.SubmissionsAndResults) {
                    // Handle nested fetch
                    var fetchPath = option.getFetchPath().split("\\.");
                    var submissionsFetch = root.fetch(fetchPath[0], JoinType.LEFT);
                    submissionsFetch.fetch(fetchPath[1], JoinType.LEFT);
                }
                else {
                    root.fetch(option.getFetchPath(), JoinType.LEFT);
                }
            }
            return null;
        };

        return findByExerciseIdElseThrow(specification, exerciseId);
    }

    Optional<SolutionProgrammingExerciseParticipation> findByProgrammingExerciseId(long programmingExerciseId);

    default SolutionProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Solution Programming Exercise Participation", programmingExerciseId));
    }
}
