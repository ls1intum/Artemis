package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface FileUploadExerciseRepository extends JpaRepository<FileUploadExercise, Long>, JpaSpecificationExecutor<FileUploadExercise> {

    @Query("""
            SELECT DISTINCT e FROM FileUploadExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<FileUploadExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories", "learningGoals" })
    Optional<FileUploadExercise> findWithEagerTeamAssignmentConfigAndCategoriesAndLearningGoalsById(Long exerciseId);

    @Query("""
            SELECT e
            FROM Course c
            LEFT JOIN  c.exercises e
            LEFT JOIN FETCH e.studentParticipations p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            Where c.id = :courseId
            AND p.student.id = :userId
            AND TYPE(e) = FileUploadExercise
            """)
    Set<FileUploadExercise> getAllFileUploadExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(@Param("courseId") long courseId,
            @Param("userId") long userId);

    /**
     * Get one file upload exercise by id.
     *
     * @param exerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default FileUploadExercise findByIdElseThrow(Long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("File Upload Exercise", exerciseId));
    }
}
