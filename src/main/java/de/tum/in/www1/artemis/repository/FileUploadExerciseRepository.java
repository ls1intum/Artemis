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

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

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
    Optional<FileUploadExercise> findWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId);

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
