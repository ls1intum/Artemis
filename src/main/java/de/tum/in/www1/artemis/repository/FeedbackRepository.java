package de.tum.in.www1.artemis.repository;

import java.util.List;

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

    List<Feedback> findAllByResult(Result result);

    List<Feedback> findAllByReferenceInAndResultSubmissionParticipationExercise(List<String> references, Exercise exercise);

    /**
     * Delete all feedbacks that belong to the given result
     * @param resultId the Id of the result where the feedbacks should be deleted
     */
    void deleteByResultId(long resultId);
}
