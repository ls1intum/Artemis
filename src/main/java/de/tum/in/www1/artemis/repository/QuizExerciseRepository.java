package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuizExercise;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

}
