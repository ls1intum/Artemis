package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ExerciseResult;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ExerciseResult entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseResultRepository extends JpaRepository<ExerciseResult, Long> {

}
