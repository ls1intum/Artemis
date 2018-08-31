package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Spring Data JPA repository for the FileUploadExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FileUploadExerciseRepository extends JpaRepository<FileUploadExercise, Long> {

    @Query("SELECT e FROM FileUploadExercise e WHERE e.course.id = :#{#courseId}")
    List<FileUploadExercise> findByCourseId(@Param("courseId") Long courseId);
}
