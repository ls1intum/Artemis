package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Exercise;

import org.springframework.data.jpa.repository.*;

import java.util.List;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
public interface ExerciseRepository extends JpaRepository<Exercise,Long> {

}
