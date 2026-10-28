package de.tum.cit.aet.artemis.metis.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;

/**
 * The review of generated questions by editors: approving one main question or all of them, changing the text of a main question, and replacing one with a newly
 * generated question.
 * <p>
 * Only main questions are reviewed; follow-up questions are asked live during the conversation and never reach a review. Every question is read through the
 * exercise the editor is authorized for, so the id of a question of another exercise is not found.
 * <p>
 * An approval releases a check in review once all its main questions are approved. A text may change only as long as the student can still receive it: the
 * question has not been shown, and the check has not been started and is ready or in review. Once the student has started, the questions of the check are
 * frozen, so that what the student answers is what the editor last saw. The statement that writes the text repeats both conditions, so a student who is shown
 * the question or starts the check between the reads of a request and its write is never overtaken by the edit. An edit that lands while another editor
 * regenerates the same question is kept: the regeneration notices the changed text and is refused rather than overwriting it.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisQuestionReviewService {

    private static final Logger log = LoggerFactory.getLogger(MetisQuestionReviewService.class);

    private static final String ENTITY_NAME = "metis";

    /** The states in which the questions of a check may still change: generated and not started. */
    private static final Set<MetisCheckStatus> EDITABLE_STATES = Set.of(MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY);

    private final MetisQuestionRepository questionRepository;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionGenerationService generationService;

    public MetisQuestionReviewService(MetisQuestionRepository questionRepository, MetisCheckRepository checkRepository, MetisQuestionGenerationService generationService) {
        this.questionRepository = questionRepository;
        this.checkRepository = checkRepository;
        this.generationService = generationService;
    }

    /**
     * Approves one main question, and releases its check to the student if it was in review and this was the last unapproved main question. A question that is
     * approved already keeps the time of its first approval.
     *
     * @param exerciseId the exercise the editor is authorized for
     * @param questionId the main question
     * @return the question as staff see it after the approval
     * @throws EntityNotFoundException  if the question does not belong to a check of the exercise
     * @throws BadRequestAlertException {@code metis.notMainQuestion} for a follow-up question
     */
    public MetisQuestionDTO approveQuestion(long exerciseId, long questionId) {
        MetisQuestion question = findMainQuestion(exerciseId, questionId);
        questionRepository.approve(questionId, TimeUtil.now());
        // Also after a repeated approval, which changes no row: a release that an interrupted earlier request left out happens now
        checkRepository.releaseIfAllMainQuestionsApproved(question.getCheckId());
        return MetisQuestionDTO.ofStaff(questionRepository.findByIdElseThrow(questionId));
    }

    /**
     * Approves every main question of an exercise that is not approved yet, and releases every check in review whose main questions are all approved then.
     *
     * @param exerciseId the exercise
     */
    public void approveAllQuestions(long exerciseId) {
        int approved = questionRepository.approveAllOpenMainQuestionsOfExercise(exerciseId, TimeUtil.now());
        int released = checkRepository.releaseApprovedChecksOfExercise(exerciseId);
        log.debug("Approved {} Metis questions and released {} checks of exercise {}", approved, released, exerciseId);
    }

    /**
     * Replaces the text of a main question that its student cannot have seen yet. The approval of the question is kept: an editor who changes a text has read
     * it.
     *
     * @param exerciseId   the exercise the editor is authorized for
     * @param questionId   the main question
     * @param questionText the new text; leading and trailing white space is dropped
     * @return the question as staff see it after the change
     * @throws EntityNotFoundException  if the question does not belong to a check of the exercise
     * @throws BadRequestAlertException {@code metis.notMainQuestion} for a follow-up question
     * @throws ConflictException        {@code metis.questionShown} once the question has been shown, {@code metis.notEditable} once the student has started the
     *                                      check or while it is neither ready nor in review
     */
    public MetisQuestionDTO updateQuestionText(long exerciseId, long questionId, String questionText) {
        MetisQuestion question = findMainQuestion(exerciseId, questionId);
        if (question.getShownAt() != null) {
            throw questionShown();
        }
        MetisCheck check = checkRepository.findByIdElseThrow(question.getCheckId());
        if (check.getStartedAt() != null || !EDITABLE_STATES.contains(check.getStatus())) {
            throw notEditable();
        }
        if (questionRepository.updateTextIfNotShown(questionId, questionText.strip()) == 0) {
            // The student was shown the question or started the check after the reads above, or the check was put back into the queue meanwhile
            boolean shownMeanwhile = questionRepository.findById(questionId).map(MetisQuestion::getShownAt).isPresent();
            throw shownMeanwhile ? questionShown() : notEditable();
        }
        return MetisQuestionDTO.ofStaff(questionRepository.findByIdElseThrow(questionId));
    }

    /**
     * Replaces a main question with a newly generated question about the same focus file, while the editor waits.
     *
     * @param exerciseId the exercise the editor is authorized for
     * @param questionId the main question
     * @return the new question as staff see it, not approved
     * @throws EntityNotFoundException           if the question does not belong to a check of the exercise
     * @throws BadRequestAlertException          {@code metis.notMainQuestion} for a follow-up question
     * @throws ConflictException                 if the question can no longer change, has been regenerated too often, the student does not consent to AI
     *                                               processing or the repository cannot be read
     * @throws InternalServerErrorAlertException if the language model is unavailable or its answer is unusable
     * @see MetisQuestionGenerationService#regenerateQuestion(long, long)
     */
    public MetisQuestionDTO regenerateQuestion(long exerciseId, long questionId) {
        return generationService.regenerateQuestion(exerciseId, questionId);
    }

    /**
     * A main question of a check of the exercise.
     */
    private MetisQuestion findMainQuestion(long exerciseId, long questionId) {
        MetisQuestion question = questionRepository.findByIdAndExerciseIdElseThrow(questionId, exerciseId);
        if (question.isFollowUp()) {
            throw new BadRequestAlertException("Follow-up questions cannot be reviewed.", ENTITY_NAME, "metis.notMainQuestion");
        }
        return question;
    }

    private static ConflictException questionShown() {
        return new ConflictException("The question has already been shown and can no longer be changed.", ENTITY_NAME, "metis.questionShown");
    }

    private static ConflictException notEditable() {
        return new ConflictException("The questions of this student can no longer be changed.", ENTITY_NAME, "metis.notEditable");
    }
}
