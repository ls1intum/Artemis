package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.MathExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the MathExercise entity.
 */
@Repository
public interface MathExerciseRepository extends JpaRepository<MathExercise, Long>, JpaSpecificationExecutor<MathExercise> {

    @Query("""
            SELECT DISTINCT e FROM MathExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<MathExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    List<MathExercise> findByAssessmentTypeAndDueDateIsAfter(AssessmentType assessmentType, ZonedDateTime dueDate);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies" })
    Optional<MathExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(Long exerciseId);

    @Query("select mathExercise from MathExercise mathExercise left join fetch mathExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.results result left join fetch result.feedbacks left join fetch result.assessor left join fetch mathExercise.teamAssignmentConfig where mathExercise.id = :#{#exerciseId}")
    Optional<MathExercise> findByIdWithExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<MathExercise> findWithStudentParticipationsAndSubmissionsById(Long exerciseId);

    @NotNull
    default MathExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Math Exercise", exerciseId));
    }

    @NotNull
    default MathExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findByIdWithExampleSubmissionsAndResults(exerciseId).orElseThrow(() -> new EntityNotFoundException("Math Exercise", exerciseId));
    }

    @NotNull
    default MathExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long exerciseId) {
        return findWithStudentParticipationsAndSubmissionsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Math Exercise", exerciseId));
    }

    /**
     * Find all exercises with *Due Date* in the future.
     *
     * @return List of Math Exercises
     */
    default List<MathExercise> findAllAutomaticAssessmentMathExercisesWithFutureDueDate() {
        return findByAssessmentTypeAndDueDateIsAfter(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now());
    }
}
