package de.tum.cit.aet.artemis.modeling.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * Spring Data JPA repository for the ModelingExercise entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ModelingExerciseRepository extends ArtemisJpaRepository<ModelingExercise, Long>, JpaSpecificationExecutor<ModelingExercise> {

    @Query("""
            SELECT DISTINCT e
            FROM ModelingExercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    List<ModelingExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exampleSubmissions", "teamAssignmentConfig", "categories", "competencyLinks.competency",
            "exampleSubmissions.submission.results" })
    Optional<ModelingExercise> findWithEagerExampleSubmissionsAndCompetenciesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks.competency" })
    Optional<ModelingExercise> findWithEagerCompetenciesById(Long exerciseId);

    @Query("""
            SELECT modelingExercise
            FROM ModelingExercise modelingExercise
                LEFT JOIN FETCH modelingExercise.exampleSubmissions exampleSubmissions
                LEFT JOIN FETCH exampleSubmissions.submission submission
                LEFT JOIN FETCH submission.results results
                LEFT JOIN FETCH results.feedbacks
                LEFT JOIN FETCH results.assessor
                LEFT JOIN FETCH modelingExercise.teamAssignmentConfig
                LEFT JOIN FETCH modelingExercise.gradingCriteria
            WHERE modelingExercise.id = :exerciseId
            """)
    Optional<ModelingExercise> findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(@Param("exerciseId") Long exerciseId);

    /**
     * Get all modeling exercises that need to be scheduled: Those must satisfy one of the following requirements:
     * <ol>
     * <li>Automatic assessment is enabled and the due date is in the future</li>
     * </ol>
     *
     * @param now the current time
     * @return List of the exercises that should be scheduled
     */
    @Query("""
            SELECT DISTINCT exercise
            FROM ModelingExercise exercise
            WHERE exercise.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC
                AND exercise.dueDate > :now
            """)
    List<ModelingExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    /**
     * Returns the modeling exercises that are part of an exam with an end date after than the provided date.
     * This method also fetches the exercise group and exam.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ModelingExercise> (can be empty)
     */
    @Query("""
            SELECT me
            FROM ModelingExercise me
                LEFT JOIN FETCH me.exerciseGroup eg
                LEFT JOIN FETCH eg.exam e
            WHERE e.endDate > :dateTime
            """)
    List<ModelingExercise> findAllWithEagerExamByExamEndDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<ModelingExercise> findWithStudentParticipationsSubmissionsResultsById(Long exerciseId);

    @Query("""
            SELECT m
            FROM ModelingExercise m
                LEFT JOIN FETCH m.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE m.title = :title
                AND m.course.id = :courseId
            """)
    Set<ModelingExercise> findAllWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId);

    /**
     * Finds a modeling exercise by its title and course id and throws a NoUniqueQueryException if multiple exercises are found.
     *
     * @param title    the title of the exercise
     * @param courseId the id of the course
     * @return the exercise with the given title and course id
     * @throws NoUniqueQueryException if multiple exercises are found with the same title
     */
    default Optional<ModelingExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        Set<ModelingExercise> allExercises = findAllWithCompetenciesByTitleAndCourseId(title, courseId);
        if (allExercises.size() > 1) {
            throw new NoUniqueQueryException("Found multiple exercises with title " + title + " in course with id " + courseId);
        }
        return allExercises.stream().findFirst();
    }

    @NotNull
    default ModelingExercise findWithEagerExampleSubmissionsAndCompetenciesByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerExampleSubmissionsAndCompetenciesById(exerciseId), exerciseId);
    }

    @NotNull
    default ModelingExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return getValueElseThrow(findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(exerciseId), exerciseId);
    }

    @NotNull
    default ModelingExercise findByIdWithStudentParticipationsSubmissionsResultsElseThrow(long exerciseId) {
        return getValueElseThrow(findWithStudentParticipationsSubmissionsResultsById(exerciseId), exerciseId);
    }

    @NotNull
    default ModelingExercise findWithEagerCompetenciesByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerCompetenciesById(exerciseId), exerciseId);
    }
}
