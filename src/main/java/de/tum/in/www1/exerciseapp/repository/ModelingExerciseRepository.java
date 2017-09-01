package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.ModelingExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the ModelingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingExerciseRepository extends JpaRepository<ModelingExercise, Long> {

}
