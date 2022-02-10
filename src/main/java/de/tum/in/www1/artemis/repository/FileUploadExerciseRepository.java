package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the FileUploadExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FileUploadExerciseRepository extends JpaRepository<FileUploadExercise, Long> {

    @Query("""
            SELECT DISTINCT e FROM FileUploadExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<FileUploadExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories" })
    Optional<FileUploadExercise> findWithEagerTeamAssignmentConfigAndCategoriesById(Long exerciseId);

    /**
     * Get one file upload exercise by id.
     *
     * @param exerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default FileUploadExercise findOneByIdElseThrow(Long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("File Upload Exercise", exerciseId));
    }
}
