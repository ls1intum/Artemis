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
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class LongFeedbackTextResource {

    private static final Logger log = LoggerFactory.getLogger(LongFeedbackTextResource.class);

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    public LongFeedbackTextResource(LongFeedbackTextRepository longFeedbackTextRepository, ParticipationAuthorizationCheckService participationAuthorizationCheckService,
            TestCaseFeedbackRepository testCaseFeedbackRepository) {
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
    }

    /**
     * Gets the long feedback associated with the specified feedback.
     * <p>
     * Negative ids are synthetic ids of automatic test-case feedback (see
     * {@link ProgrammingFeedbackSynthesizerService}): they encode {@code (resultId, seq)} of a
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
        long resultId = ProgrammingFeedbackSynthesizerService.resultIdFromSyntheticId(syntheticFeedbackId);
        int seq = ProgrammingFeedbackSynthesizerService.seqFromSyntheticId(syntheticFeedbackId);
        TestCaseFeedback feedback = testCaseFeedbackRepository.findWithMessageAndParticipationByResultIdAndSeq(resultId, seq)
                .orElseThrow(() -> new EntityNotFoundException("TestCaseFeedback", syntheticFeedbackId));
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(feedback.getResult().getSubmission().getParticipation());
        String message = feedback.getMessageText();
        if (message == null) {
            throw new EntityNotFoundException("TestCaseFeedback message", syntheticFeedbackId);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(message);
    }

    private void checkCanAccessResultElseThrow(final LongFeedbackText longFeedbackText) {
        final Result result = longFeedbackText.getFeedback().getResult();
        final Participation participation = result.getSubmission().getParticipation();
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
    }
}
