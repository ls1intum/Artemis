package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

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
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@Repository
public interface TextExerciseRepository extends JpaRepository<TextExercise, Long>, JpaSpecificationExecutor<TextExercise> {

    @Query("""
            SELECT DISTINCT e
            FROM TextExercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    List<TextExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies", "plagiarismDetectionConfig" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "textExercise.exampleSubmissions.submission.results.feedbacks", "textExercise.exampleSubmissions.submission.results.assessor",
            "textExercise.exampleSubmissions.submission.blocks", "textExercise.teamAssignmentConfig" })
    Optional<TextExercise> findWithExampleSubmissionsAndResultsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TextExercise> findWithStudentParticipationsAndSubmissionsById(Long exerciseId);

    @NotNull
    default TextExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @Query("""
            SELECT DISTINCT e
            FROM TextExercise e
                LEFT JOIN FETCH e.gradingCriteria
            WHERE e.id = :exerciseId
            """)
    Optional<TextExercise> findByIdWithGradingCriteria(long exerciseId);

    @NotNull
    default TextExercise findByIdWithGradingCriteriaElseThrow(long exerciseId) {
        return findByIdWithGradingCriteria(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @NotNull
    default TextExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findWithExampleSubmissionsAndResultsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @NotNull
    default TextExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long exerciseId) {
        return findWithStudentParticipationsAndSubmissionsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }
}
