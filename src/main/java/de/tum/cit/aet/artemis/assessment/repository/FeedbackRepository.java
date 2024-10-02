package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the Feedback entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface FeedbackRepository extends ArtemisJpaRepository<Feedback, Long> {

    List<Feedback> findByResult(Result result);

    List<Feedback> findByReferenceInAndResult_Submission_Participation_Exercise(List<String> references, Exercise exercise);

    @Query("""
            SELECT feedback
            FROM Feedback feedback
            WHERE feedback.gradingInstruction.id IN :gradingInstructionsIds
            """)
    List<Feedback> findFeedbackByGradingInstructionIds(@Param("gradingInstructionsIds") List<Long> gradingInstructionsIds);

    @Query("""
            SELECT COUNT(*) > 0
            FROM Feedback feedback
            WHERE feedback.gradingInstruction.id IN :gradingInstructionsIds
            """)
    boolean hasFeedbackFromGradingInstructionIds(@Param("gradingInstructionsIds") List<Long> gradingInstructionsIds);

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
     * Given the grading criteria, collects each sub grading instructions in a list.
     * Then, find all feedback that matches with the grading instructions ids
     *
     * @param gradingCriteria The grading criteria belongs to exercise in a specific course
     * @return list including feedback entries which are associated with the grading instructions
     */
    default List<Feedback> findFeedbackByExerciseGradingCriteria(Set<GradingCriterion> gradingCriteria) {
        if (gradingCriteria.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> gradingInstructionsIds = gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                .map(GradingInstruction::getId).toList();
        return findFeedbackByGradingInstructionIds(gradingInstructionsIds);
    }

    /**
     * Given the grading criteria, this method checks if any criteria
     * is used in some feedback.
     *
     * @param gradingCriteria The grading criteria belongs to exercise in a specific course
     * @return true if any grading criteria gets used in any feedback
     */
    default boolean hasFeedbackByExerciseGradingCriteria(Set<GradingCriterion> gradingCriteria) {
        if (gradingCriteria.isEmpty()) {
            return false;
        }
        List<Long> gradingInstructionsIds = gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                .map(GradingInstruction::getId).toList();
        return hasFeedbackFromGradingInstructionIds(gradingInstructionsIds);
    }

    /**
     * Deletes {@link Feedback} entries where the associated {@link Result} has no submission and no participation.
     * Returns {@code void}
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IN (SELECT r
                               FROM Result r
                               WHERE r.submission IS NULL
                                   AND r.participation IS NULL)
            """)
    void deleteFeedbackForOrphanResults();

    /**
     * Deletes {@link Feedback} entries with a {@code null} result.
     * Returns {@code void}
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM Feedback f WHERE f.result IS NULL
            """)
    void deleteOrphanFeedback();

    /**
     * Deletes {@link Feedback} entries associated with rated {@link Result} that are not the latest rated result
     * for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes old feedback entries that are not part of the latest rated results, for courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     *                       Returns {@code void}
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IN (SELECT r
                               FROM Result r
                               JOIN r.participation p
                               LEFT JOIN p.exercise e
                               LEFT JOIN e.course c
                               WHERE r.id NOT IN (SELECT MAX(r2.id)
                                                  FROM Result r2
                                                  WHERE r2.participation.id = p.id
                                                      AND r2.rated=true
                                                  )
                                   AND c.endDate < :deleteTo
                                   AND c.startDate > :deleteFrom
                               )
            """)
    void deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes non-rated {@link Feedback} entries where the associated course's start and end dates
     * are between the specified date range.
     * This query deletes feedback for non-rated results within courses whose end date is before
     * {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     *                       Returns {@code void}
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IN (SELECT r
                               FROM Result r
                               JOIN r.participation p
                               JOIN p.exercise e
                               JOIN e.course c
                               WHERE r.rated=false
                                   AND c.endDate < :deleteTo
                                   AND c.startDate > :deleteFrom
                               )
            """)
    void deleteOldNonRatedFeedbackWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
