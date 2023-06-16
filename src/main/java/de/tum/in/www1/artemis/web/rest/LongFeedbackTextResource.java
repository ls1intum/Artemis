package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.LongFeedbackTextRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@RestController
@RequestMapping("/api")
public class LongFeedbackTextResource {

    private static final Logger log = LoggerFactory.getLogger(LongFeedbackTextResource.class);

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    public LongFeedbackTextResource(LongFeedbackTextRepository longFeedbackTextRepository, ParticipationAuthorizationCheckService participationAuthorizationCheckService) {
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
    }

    /**
     * Gets the long feedback associated with the specified feedback.
     *
     * @param resultId   The result the feedback belongs to.
     * @param feedbackId The feedback for which the long feedback should be fetched.
     * @return The long feedback belonging to the feedback with id {@code feedbackId}.
     */
    @GetMapping("results/{resultId}/feedbacks/{feedbackId}/long-feedback")
    @EnforceAtLeastStudent
    public ResponseEntity<LongFeedbackText> getLongFeedback(@PathVariable Long resultId, @PathVariable Long feedbackId) {
        log.debug("REST request to get long feedback: {} (result: {})", feedbackId, resultId);

        final LongFeedbackText longFeedbackText = longFeedbackTextRepository.findByIdWithFeedbackAndResultAndParticipationElseThrow(feedbackId);
        checkCanAccessResultElseThrow(resultId, longFeedbackText);

        return ResponseEntity.ok(longFeedbackText);
    }

    private void checkCanAccessResultElseThrow(final Long resultId, final LongFeedbackText longFeedbackText) {
        final Result result = longFeedbackText.getFeedback().getResult();
        if (!result.getId().equals(resultId)) {
            throw new BadRequestAlertException("resultId of the path does not correspond to feedbackId", "result", "invalidResultId");
        }

        final Participation participation = result.getParticipation();
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
    }
}
