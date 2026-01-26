package de.tum.cit.aet.artemis.text.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@Conditional(TextEnabled.class)
@Lazy
@Repository
public interface TextExerciseRepository extends ArtemisJpaRepository<TextExercise, Long>, JpaSpecificationExecutor<TextExercise> {

    @Query("""
            SELECT DISTINCT e
            FROM TextExercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    List<TextExercise> findByCourseIdWithCategories(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks.competency", "categories" })
    Optional<TextExercise> findWithEagerCompetenciesAndCategoriesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencyLinks.competency" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencyLinks.competency", "plagiarismDetectionConfig" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(long exerciseId);

    @Query("""
            SELECT t
            FROM TextExercise t
                LEFT JOIN FETCH t.teamAssignmentConfig
            WHERE t.id = :exerciseId
            """)
    Optional<TextExercise> findWithTeamAssignmentConfigById(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT textExercise
            FROM TextExercise textExercise
                LEFT JOIN FETCH textExercise.teamAssignmentConfig
                LEFT JOIN FETCH textExercise.gradingCriteria
            WHERE textExercise.id = :exerciseId
            """)
    Optional<TextExercise> findWithTeamAssignmentConfigAndGradingCriteriaById(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TextExercise> findWithStudentParticipationsAndSubmissionsById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "gradingCriteria" })
    Optional<TextExercise> findWithGradingCriteriaById(long exerciseId);

    @Query("""
            SELECT t
            FROM TextExercise t
                LEFT JOIN FETCH t.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE t.title = :title
                AND t.course.id = :courseId
            """)
    Set<TextExercise> findAllWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId);

    /**
     * Finds a TextExercise with minimal data necessary for exercise versioning.
     * Only includes core configuration data, NOT submissions, results, or example submissions.
     * Basic TextExercise fields (exampleSolution) are already included in the entity.
     *
     * @param exerciseId the id of the exercise to fetch
     * @return {@link TextExercise}
     */
    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks", "categories", "teamAssignmentConfig", "gradingCriteria", "plagiarismDetectionConfig" })
    Optional<TextExercise> findForVersioningById(long exerciseId);

    /**
     * Finds a text exercise by its title and course id and throws a NoUniqueQueryException if multiple exercises are found.
     *
     * @param title    the title of the exercise
     * @param courseId the id of the course
     * @return the exercise with the given title and course id
     * @throws NoUniqueQueryException if multiple exercises are found with the same title
     */
    default Optional<TextExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        Set<TextExercise> allExercises = findAllWithCompetenciesByTitleAndCourseId(title, courseId);
        if (allExercises.size() > 1) {
            throw new NoUniqueQueryException("Found multiple exercises with title " + title + " in course with id " + courseId);
        }
        return allExercises.stream().findFirst();
    }

    @NonNull
    default TextExercise findWithGradingCriteriaByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithGradingCriteriaById(exerciseId), exerciseId);
    }

    @NonNull
    default TextExercise findWithEagerCompetenciesAndCategoriesByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithEagerCompetenciesAndCategoriesById(exerciseId), exerciseId);
    }

    @NonNull
    default TextExercise findWithTeamAssignmentConfigByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithTeamAssignmentConfigById(exerciseId), exerciseId);
    }

    @NonNull
    default TextExercise findWithTeamAssignmentConfigAndGradingCriteriaByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithTeamAssignmentConfigAndGradingCriteriaById(exerciseId), exerciseId);
    }

    @NonNull
    default TextExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long exerciseId) {
        return getValueElseThrow(findWithStudentParticipationsAndSubmissionsById(exerciseId), exerciseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "categories" })
    List<TextExercise> findAllWithCategoriesByCourseId(Long courseId);
}
