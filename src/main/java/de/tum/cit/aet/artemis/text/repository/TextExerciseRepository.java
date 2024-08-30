package de.tum.cit.aet.artemis.text.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

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

    @EntityGraph(type = LOAD, attributePaths = { "competencies" })
    Optional<TextExercise> findWithEagerCompetenciesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies", "plagiarismDetectionConfig" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(long exerciseId);

    @Query("""
            SELECT t
            FROM TextExercise t
                LEFT JOIN FETCH t.exampleSubmissions e
                LEFT JOIN FETCH e.submission s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH s.blocks
                LEFT JOIN FETCH r.assessor
                LEFT JOIN FETCH t.teamAssignmentConfig
            WHERE t.id = :exerciseId
            """)
    Optional<TextExercise> findWithExampleSubmissionsAndResultsById(@Param("exerciseId") long exerciseId);

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
                LEFT JOIN FETCH textExercise.gradingCriteria
            WHERE textExercise.id = :exerciseId
            """)
    Optional<TextExercise> findWithExampleSubmissionsAndResultsAndGradingCriteriaById(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TextExercise> findWithStudentParticipationsAndSubmissionsById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "gradingCriteria" })
    Optional<TextExercise> findWithGradingCriteriaById(long exerciseId);

    /**
     * Finds a text exercise by title and course id. Currently, name duplicates are allowed but this method throws an exception if multiple exercises with
     * the same title are found.
     *
     * @param title    the title of the exercise
     * @param courseId the id of the course containing the exercise
     * @return the exercise with the given title and course id
     * @throws NonUniqueResultException if multiple exercises with the same name in the same course are found
     */
    @Query("""
            SELECT t
            FROM TextExercise t
                LEFT JOIN FETCH t.competencies
            WHERE t.title = :title
                AND t.course.id = :courseId
            """)
    Optional<TextExercise> findWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId) throws NonUniqueResultException;

    @NotNull
    default TextExercise findWithGradingCriteriaByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithGradingCriteriaById(exerciseId), exerciseId);
    }

    @NotNull
    default TextExercise findWithEagerCompetenciesByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerCompetenciesById(exerciseId), exerciseId);
    }

    @NotNull
    default TextExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return getValueElseThrow(findWithExampleSubmissionsAndResultsById(exerciseId), exerciseId);
    }

    @NotNull
    default TextExercise findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(long exerciseId) {
        return getValueElseThrow(findWithExampleSubmissionsAndResultsAndGradingCriteriaById(exerciseId), exerciseId);
    }

    @NotNull
    default TextExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long exerciseId) {
        return getValueElseThrow(findWithStudentParticipationsAndSubmissionsById(exerciseId), exerciseId);
    }
}
