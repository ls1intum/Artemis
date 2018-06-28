package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ModelingExercise;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the ModelingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingExerciseRepository extends JpaRepository<ModelingExercise, Long> {

}
