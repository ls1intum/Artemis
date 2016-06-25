package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByParticipationIdOrderByBuildCompletionDateDesc(Long participationId);

    List<Result> findByParticipationExerciseIdOrderByBuildCompletionDateAsc(Long exerciseId);

    @Query("select r from Result r where r.buildCompletionDate = (select max(rr.buildCompletionDate) from Result rr where rr.participation.student.id = r.participation.student.id) and r.participation.exercise.id = :exerciseId order by r.buildCompletionDate asc")
    List<Result> findLatestResultsForExercise(@Param("exerciseId") Long exerciseId);

    @Query("select r from Result r where r.buildCompletionDate = (select min(rr.buildCompletionDate) from Result rr where rr.participation.student.id = r.participation.student.id and rr.buildSuccessful = true) and r.participation.exercise.id = :exerciseId and r.buildSuccessful = true order by r.buildCompletionDate asc")
    List<Result> findLatestSuccessfulResultsForExercise(@Param("exerciseId") Long exerciseId);

    List<Result> findByParticipationExerciseIdAndBuildSuccessfulOrderByBuildCompletionDateAsc(Long exerciseId, boolean buildSuccessful);
}
