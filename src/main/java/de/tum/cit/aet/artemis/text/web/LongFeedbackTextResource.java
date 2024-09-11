package de.tum.cit.aet.artemis.text.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.service.ParticipationAuthorizationCheckService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
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
     * @return The long feedback text belonging to the feedback with id {@code feedbackId}.
     */
    @GetMapping("results/{resultId}/feedbacks/{feedbackId}/long-feedback")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getLongFeedback(@PathVariable Long resultId, @PathVariable Long feedbackId) {
        log.debug("REST request to get long feedback: {} (result: {})", feedbackId, resultId);

        final LongFeedbackText longFeedbackText = longFeedbackTextRepository.findByFeedbackIdWithFeedbackAndResultAndParticipationElseThrow(feedbackId);
        checkCanAccessResultElseThrow(resultId, longFeedbackText);

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(longFeedbackText.getText());
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
