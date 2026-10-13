package de.tum.cit.aet.artemis.metis.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionTextRequestDTO;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionReviewService;

/**
 * REST resource for the review of the generated questions of an exercise: approving one main question or all of them, changing the text of a main question and
 * replacing one with a newly generated question.
 * <p>
 * Every path names the exercise, which the role check needs, and the service reads the question through it, so a question of another exercise is not found.
 * Follow-up questions are never reviewed.
 */
@Conditional(MetisEnabled.class)
@Lazy
@FeatureUsage(UserFeature.METIS_CHECK_MANAGEMENT)
@RestController
@RequestMapping("api/metis/")
public class MetisQuestionReviewResource {

    private static final Logger log = LoggerFactory.getLogger(MetisQuestionReviewResource.class);

    private final MetisQuestionReviewService questionReviewService;

    public MetisQuestionReviewResource(MetisQuestionReviewService questionReviewService) {
        this.questionReviewService = questionReviewService;
    }

    /**
     * POST exercises/{exerciseId}/questions/{questionId}/approve : approves a main question, and releases its check once all its main questions are approved.
     * Approving an approved question changes nothing.
     *
     * @param exerciseId the exercise
     * @param questionId the main question
     * @return 200 with the approved question; 400 for a follow-up question; 404 if the question does not belong to the exercise
     */
    @PostMapping("exercises/{exerciseId}/questions/{questionId}/approve")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<MetisQuestionDTO> approveQuestion(@PathVariable long exerciseId, @PathVariable long questionId) {
        log.debug("REST request to approve the Metis question {} of exercise {}", questionId, exerciseId);
        return ResponseEntity.ok(questionReviewService.approveQuestion(exerciseId, questionId));
    }

    /**
     * POST exercises/{exerciseId}/questions/approve-all : approves every main question of an exercise that is not approved yet, and releases every check whose
     * main questions are all approved then.
     *
     * @param exerciseId the exercise
     * @return 200 without a body
     */
    @PostMapping("exercises/{exerciseId}/questions/approve-all")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> approveAllQuestions(@PathVariable long exerciseId) {
        log.debug("REST request to approve all Metis questions of exercise {}", exerciseId);
        questionReviewService.approveAllQuestions(exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT exercises/{exerciseId}/questions/{questionId}/text : changes the text of a main question that its student cannot have seen yet; its approval is kept.
     *
     * @param exerciseId the exercise
     * @param questionId the main question
     * @param text       the new text of the question
     * @return 200 with the changed question; 400 if the text is blank or longer than 2000 characters, or for a follow-up question; 404 if the question does
     *         not belong to the exercise; 409 if the question has been shown, or the student has started the check or it is neither ready nor in review
     */
    @PutMapping("exercises/{exerciseId}/questions/{questionId}/text")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<MetisQuestionDTO> updateQuestionText(@PathVariable long exerciseId, @PathVariable long questionId, @Valid @RequestBody MetisQuestionTextRequestDTO text) {
        log.debug("REST request to change the text of the Metis question {} of exercise {}", questionId, exerciseId);
        return ResponseEntity.ok(questionReviewService.updateQuestionText(exerciseId, questionId, text.questionText()));
    }

    /**
     * POST exercises/{exerciseId}/questions/{questionId}/regenerate : replaces a main question with a newly generated question about the same focus file. The
     * request waits for the language model; the new question is not approved.
     *
     * @param exerciseId the exercise
     * @param questionId the main question
     * @return 200 with the new question; 400 for a follow-up question; 404 if the question does not belong to the exercise; 409 if the question has been shown,
     *         has been regenerated too often or changed meanwhile, the student has started the check, does not consent to AI processing or the repository
     *         cannot be read; 500 if the language model is unavailable or its answer is unusable
     */
    @PostMapping("exercises/{exerciseId}/questions/{questionId}/regenerate")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<MetisQuestionDTO> regenerateQuestion(@PathVariable long exerciseId, @PathVariable long questionId) {
        log.debug("REST request to regenerate the Metis question {} of exercise {}", questionId, exerciseId);
        return ResponseEntity.ok(questionReviewService.regenerateQuestion(exerciseId, questionId));
    }
}
