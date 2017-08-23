package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

}
