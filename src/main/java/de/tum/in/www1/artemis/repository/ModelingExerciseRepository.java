package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ModelingExercise;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ModelingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingExerciseRepository extends JpaRepository<ModelingExercise, Long> {

}
