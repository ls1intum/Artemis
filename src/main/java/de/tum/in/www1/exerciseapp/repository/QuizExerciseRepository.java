package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

}
