package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;

@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("grading/long-feedback")
@RestController
@RequestMapping("api/assessment/")
public class LongFeedbackTextResource {

    private static final Logger log = LoggerFactory.getLogger(LongFeedbackTextResource.class);

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ResultService resultService;

    public LongFeedbackTextResource(LongFeedbackTextRepository longFeedbackTextRepository, ParticipationAuthorizationCheckService participationAuthorizationCheckService,
            TestCaseFeedbackRepository testCaseFeedbackRepository, AuthorizationCheckService authorizationCheckService, ResultService resultService) {
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.resultService = resultService;
    }

    /**
     * Gets the long feedback associated with the specified feedback.
     * <p>
     * Negative ids are synthetic ids of automatic test-case feedback (see
     * {@link ProgrammingFeedbackSynthesizerService}): they encode the id of a
     * {@code test_case_feedback} row whose full message is served from the deduplicated message table.
     *
     * @param feedbackId The feedback for which the long feedback should be fetched.
     * @return The long feedback text belonging to the feedback with id {@code feedbackId}.
     */
    @GetMapping("feedbacks/{feedbackId}/long-feedback")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getLongFeedback(@PathVariable Long feedbackId) {
        log.debug("REST request to get long feedback: {}", feedbackId);

        if (ProgrammingFeedbackSynthesizerService.isSyntheticId(feedbackId)) {
            return getLongFeedbackForTestCaseFeedback(feedbackId);
        }

        final LongFeedbackText longFeedbackText = longFeedbackTextRepository.findByFeedbackIdWithFeedbackAndResultAndParticipationElseThrow(feedbackId);
        checkCanAccessResultElseThrow(longFeedbackText);

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(longFeedbackText.getText());
    }

    private ResponseEntity<String> getLongFeedbackForTestCaseFeedback(long syntheticFeedbackId) {
        if (ProgrammingFeedbackSynthesizerService.isSyntheticScaId(syntheticFeedbackId)) {
            // SCA views carry their whole message inline, so there is no long feedback to serve for them
            throw new EntityNotFoundException("TestCaseFeedback", syntheticFeedbackId);
        }
        long rowId = ProgrammingFeedbackSynthesizerService.rowIdFromSyntheticId(syntheticFeedbackId);
        TestCaseFeedback feedback = testCaseFeedbackRepository.findWithMessageAndParticipationById(rowId)
                .orElseThrow(() -> new EntityNotFoundException("TestCaseFeedback", syntheticFeedbackId));
        Participation participation = feedback.getResult().getSubmission().getParticipation();
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
        checkTestCaseVisibilityElseThrow(feedback, participation, syntheticFeedbackId);
        String message = feedback.getMessageText();
        if (message == null) {
            throw new EntityNotFoundException("TestCaseFeedback message", syntheticFeedbackId);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(message);
    }

    /**
     * The synthetic ids encode typed row ids and are enumerable, so unlike the surrogate ids of manual
     * feedback they cannot act as capability tokens: without this check a student could fetch the messages
     * of hidden (visibility NEVER) or not-yet-visible (AFTER_DUE_DATE before the due date) test cases of
     * their own submission, which every other read path filters out (see Result#filterSensitiveFeedbacks).
     * The 'not yet visible' half has to use the very predicate those read paths use
     * ({@code ResultService#shouldHideAfterDueDateFeedback}) rather than a due-date check of its own:
     * an exam hides such feedback until the results are published, and a course exercise hides automatic
     * feedback until the last individual due date has passed, both of which outlast this participation's
     * own due date.
     */
    private void checkTestCaseVisibilityElseThrow(TestCaseFeedback feedback, Participation participation, long syntheticFeedbackId) {
        if (authorizationCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise())) {
            return;
        }
        boolean hiddenBeforeDueDate = feedback.isAfterDueDate() && resultService.shouldHideAfterDueDateFeedback(participation, feedback.getResult().getAssessmentType());
        if (feedback.isInvisible() || hiddenBeforeDueDate) {
            // 404 (not 403) so that the existence of hidden test feedback is not revealed either
            throw new EntityNotFoundException("TestCaseFeedback message", syntheticFeedbackId);
        }
    }

    private void checkCanAccessResultElseThrow(final LongFeedbackText longFeedbackText) {
        final Result result = longFeedbackText.getFeedback().getResult();
        final Participation participation = result.getSubmission().getParticipation();
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
    }
}
