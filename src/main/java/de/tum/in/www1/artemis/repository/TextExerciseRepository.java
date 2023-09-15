package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@Repository
public interface TextExerciseRepository extends JpaRepository<TextExercise, Long>, JpaSpecificationExecutor<TextExercise> {

    @Query("""
                SELECT DISTINCT e FROM TextExercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<TextExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(Long exerciseId);

    List<TextExercise> findByAssessmentTypeAndDueDateIsAfter(AssessmentType assessmentType, ZonedDateTime dueDate);

    @Query("select textExercise from TextExercise textExercise left join fetch textExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.results result left join fetch result.feedbacks left join fetch submission.blocks left join fetch result.assessor left join fetch textExercise.teamAssignmentConfig where textExercise.id = :#{#exerciseId}")
    Optional<TextExercise> findByIdWithExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TextExercise> findWithStudentParticipationsAndSubmissionsById(Long exerciseId);

    @NotNull
    default TextExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @NotNull
    default TextExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findByIdWithExampleSubmissionsAndResults(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @NotNull
    default TextExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long exerciseId) {
        return findWithStudentParticipationsAndSubmissionsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    /**
     * Find all exercises with *Due Date* in the future.
     *
     * @return List of Text Exercises
     */
    default List<TextExercise> findAllAutomaticAssessmentTextExercisesWithFutureDueDate() {
        return findByAssessmentTypeAndDueDateIsAfter(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now());
    }
}
