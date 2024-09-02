package de.tum.cit.aet.artemis.fileupload.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * Spring Data JPA repository for the FileUploadExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface FileUploadExerciseRepository extends ArtemisJpaRepository<FileUploadExercise, Long>, JpaSpecificationExecutor<FileUploadExercise> {

    @Query("""
            SELECT DISTINCT e FROM FileUploadExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<FileUploadExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencies" })
    Optional<FileUploadExercise> findWithEagerCompetenciesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "competencies" })
    Optional<FileUploadExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(Long exerciseId);

    @Query("""
            SELECT f
            FROM FileUploadExercise f
                LEFT JOIN FETCH f.competencies
            WHERE f.title = :title
                AND f.course.id = :courseId
            """)
    Set<FileUploadExercise> findAllWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId) throws NonUniqueResultException;

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

    @NotNull
    default FileUploadExercise findWithEagerCompetenciesByIdElseThrow(Long exerciseId) {
        return getValueElseThrow(findWithEagerCompetenciesById(exerciseId), exerciseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "gradingCriteria" })
    Optional<FileUploadExercise> findWithGradingCriteriaById(Long exerciseId);

    @NotNull
    default FileUploadExercise findWithGradingCriteriaByIdElseThrow(Long exerciseId) {
        return getValueElseThrow(findWithGradingCriteriaById(exerciseId), exerciseId);
    }
}
