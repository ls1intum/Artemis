package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the FileUploadExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FileUploadExerciseRepository extends JpaRepository<FileUploadExercise, Long> {

}
