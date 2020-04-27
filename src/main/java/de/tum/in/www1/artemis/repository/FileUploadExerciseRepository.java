package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.FileUploadExercise;

/**
 * Spring Data JPA repository for the FileUploadExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FileUploadExerciseRepository extends JpaRepository<FileUploadExercise, Long> {

    @Query("SELECT e FROM FileUploadExercise e WHERE e.course.id = :#{#courseId}")
    List<FileUploadExercise> findByCourseId(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories" })
    Optional<FileUploadExercise> findWithEagerTeamAssignmentConfigAndCategoriesById(Long exerciseId);
}
