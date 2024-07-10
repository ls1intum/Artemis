package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextExerciseRepository extends ArtemisJpaRepository<TextExercise, Long>, JpaSpecificationExecutor<TextExercise> {

    @Query("""
            SELECT DISTINCT e
            FROM TextExercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    List<TextExercise> findByCourseIdWithCategories(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies", "plagiarismDetectionConfig" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(long exerciseId);

    @Query("""
            SELECT textExercise
            FROM TextExercise textExercise
                LEFT JOIN FETCH textExercise.exampleSubmissions exampleSubmissions
                LEFT JOIN FETCH exampleSubmissions.submission submission
                LEFT JOIN FETCH submission.results result
                LEFT JOIN FETCH result.feedbacks
                LEFT JOIN FETCH submission.blocks
                LEFT JOIN FETCH result.assessor
                LEFT JOIN FETCH textExercise.teamAssignmentConfig
            WHERE textExercise.id = :exerciseId
            """)
    Optional<TextExercise> findWithExampleSubmissionsAndResultsById(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TextExercise> findWithStudentParticipationsAndSubmissionsById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "gradingCriteria" })
    Optional<TextExercise> findWithGradingCriteriaById(long exerciseId);

    @NotNull
    default TextExercise findWithGradingCriteriaByIdElseThrow(long exerciseId) {
        return findWithGradingCriteriaById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
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
