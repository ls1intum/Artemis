package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ProgrammingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

}
