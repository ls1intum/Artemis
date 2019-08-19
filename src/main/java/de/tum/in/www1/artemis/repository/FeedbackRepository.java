package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;

/**
 * Spring Data JPA repository for the Feedback entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByResult(Result result);

    List<Feedback> findByReferenceInAndResult_Submission_Participation_Exercise(List<String> references, Exercise exercise);

    @EntityGraph(attributePaths = { "result", "result.submission" })
    List<Feedback> findAllByResult_Submission_Participation_ExerciseIdIn(List<Long> exerciseIds);
}
