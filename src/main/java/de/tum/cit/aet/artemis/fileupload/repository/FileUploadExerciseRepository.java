package de.tum.cit.aet.artemis.fileupload.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.NonUniqueResultException;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * Spring Data JPA repository for the FileUploadExercise entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FileUploadExerciseRepository extends ArtemisJpaRepository<FileUploadExercise, Long>, JpaSpecificationExecutor<FileUploadExercise> {

    @Query("""
            SELECT DISTINCT e FROM FileUploadExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<FileUploadExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks.competency" })
    Optional<FileUploadExercise> findWithEagerCompetenciesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencyLinks.competency" })
    Optional<FileUploadExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(Long exerciseId);

    @Query("""
            SELECT DISTINCT f
            FROM FileUploadExercise f
                 LEFT JOIN FETCH f.teamAssignmentConfig
                 LEFT JOIN FETCH f.gradingCriteria
                 LEFT JOIN FETCH f.competencyLinks cl
                 LEFT JOIN FETCH cl.competency
            WHERE f.id = :exerciseId
            """)
    Optional<FileUploadExercise> findByIdWithCompetenciesAndGradingCriteria(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT f
            FROM FileUploadExercise f
                LEFT JOIN FETCH f.competencyLinks
            WHERE f.title = :title
                AND f.course.id = :courseId
            """)
    Set<FileUploadExercise> findAllWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId) throws NonUniqueResultException;

    /**
     * Finds a FileUploadExercise with minimal data necessary for exercise versioning.
     * Only includes core configuration data, NOT submissions, results, or example submissions.
     * Basic FileUploadExercise fields (exampleSolution, filePattern) are already included in the entity.
     *
     * @param exerciseId the id of the exercise to find
     * @return the exercise with minimal data necessary for exercise versioning
     */
    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks", "categories", "teamAssignmentConfig", "gradingCriteria", "plagiarismDetectionConfig" })
    Optional<FileUploadExercise> findForVersioningById(long exerciseId);

    /**
     * Finds a file upload exercise by its title and course id and throws a NoUniqueQueryException if multiple exercises are found.
     *
     * @param title    the title of the exercise
     * @param courseId the id of the course
     * @return the exercise with the given title and course id
     * @throws NoUniqueQueryException if multiple exercises are found with the same title
     */
    default Optional<FileUploadExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        Set<FileUploadExercise> allExercises = findAllWithCompetenciesByTitleAndCourseId(title, courseId);
        if (allExercises.size() > 1) {
            throw new NoUniqueQueryException("Found multiple exercises with title " + title + " in course with id " + courseId);
        }
        return allExercises.stream().findFirst();
    }

    @NonNull
    default FileUploadExercise findWithEagerCompetenciesByIdElseThrow(Long exerciseId) {
        return getValueElseThrow(findWithEagerCompetenciesById(exerciseId), exerciseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "gradingCriteria" })
    Optional<FileUploadExercise> findWithGradingCriteriaById(Long exerciseId);

    @NonNull
    default FileUploadExercise findWithGradingCriteriaByIdElseThrow(Long exerciseId) {
        return getValueElseThrow(findWithGradingCriteriaById(exerciseId), exerciseId);
    }
}
