package de.tum.in.www1.artemis.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;

/**
 * Spring Data JPA repository for the Feedback entity.
 */
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByResult(Result result);

    List<Feedback> findByReferenceInAndResult_Submission_Participation_Exercise(List<String> references, Exercise exercise);

    @Query("select feedback from Feedback feedback where feedback.gradingInstruction.id in :gradingInstructionsIds")
    List<Feedback> findFeedbackByGradingInstructionIds(@Param("gradingInstructionsIds") List<Long> gradingInstructionsIds);

    /**
     * Save the given feedback elements to the database in case they are not yet connected to a result
     *
     * @param feedbackList the feedback items that should be saved
     * @return all elements of the original list with the saved feedback items (i.e. the ones without result) having an id now.
     */
    default List<Feedback> saveFeedbacks(List<Feedback> feedbackList) {
        List<Feedback> updatedFeedbackList = new ArrayList<>();
        for (var feedback : feedbackList) {
            if (feedback.getResult() == null) {
                // only save feedback not yet connected to a result
                updatedFeedbackList.add(save(feedback));
            }
            else {
                updatedFeedbackList.add(feedback);
            }
        }
        return updatedFeedbackList;
    }

    /**
     * Find all existing Feedback Elements referencing a text block part of a TextCluster.
     *
     * @param cluster TextCluster requesting existing Feedbacks for.
     * @return Map<TextBlockId, Feedback>
     */
    default Map<String, Feedback> getFeedbackForTextExerciseInCluster(TextCluster cluster) {
        final List<String> references = cluster.getBlocks().stream().map(TextBlock::getId).toList();
        final TextExercise exercise = cluster.getExercise();
        return findByReferenceInAndResult_Submission_Participation_Exercise(references, exercise).parallelStream()
                .collect(Collectors.toMap(Feedback::getReference, feedback -> feedback));
    }

    /**
     * Given the grading criteria, collects each sub grading instructions in a list.
     * Then, find all feedback that matches with the grading instructions ids
     *
     * @param gradingCriteria The grading criteria belongs to exercise in a specific course
     * @return list including feedback entries which are associated with the grading instructions
     */
    default List<Feedback> findFeedbackByExerciseGradingCriteria(List<GradingCriterion> gradingCriteria) {
        List<Long> gradingInstructionsIds = gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                .map(GradingInstruction::getId).toList();
        return findFeedbackByGradingInstructionIds(gradingInstructionsIds);
    }
}
