package de.tum.cit.aet.artemis.assessment.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;

@Lazy
@Repository
@Primary
public interface ResultTestRepository extends ResultRepository {

    @EntityGraph(type = LOAD, attributePaths = "submission")
    Optional<Result> findResultWithSubmissionsById(long resultId);

    Set<Result> findAllBySubmissionParticipationExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks" })
    Set<Result> findAllWithEagerFeedbackByAssessorIsNotNullAndSubmission_Participation_ExerciseIdAndCompletionDateIsNotNull(long exerciseId);

    Optional<Result> findDistinctBySubmissionId(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findDistinctWithFeedbackBySubmissionId(long submissionId);

    List<Result> findBySubmissionParticipationIdOrderByCompletionDateDesc(long participationId);

    default Result findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDescElseThrow(long participationId) {
        return getValueElseThrow(findFirstWithFeedbacksTestCasesByParticipationIdOrderByCompletionDateDesc(participationId));
    }

    /**
     * Finds the first result by participation ID, including its submissions, ordered by completion date in descending order.
     * This method avoids in-memory paging by retrieving the first result directly from the database.
     *
     * @param participationId the ID of the participation to find the result for
     * @return an {@code Optional} containing the first {@code Result} with submissions, ordered by completion date in descending order,
     *         or an empty {@code Optional} if no result is found
     */
    default Optional<Result> findFirstWithSubmissionsByParticipationIdOrderByCompletionDateDesc(long participationId) {
        var resultOptional = findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(participationId);
        if (resultOptional.isEmpty()) {
            return Optional.empty();
        }
        var id = resultOptional.get().getId();
        return findResultWithSubmissionsById(id);
    }
}
