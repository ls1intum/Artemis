package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.LongFeedbackRepository;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@RestController
@RequestMapping("/api")
public class LongFeedbackResource {

    private static final Logger log = LoggerFactory.getLogger(LongFeedbackResource.class);

    private final LongFeedbackRepository longFeedbackRepository;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    public LongFeedbackResource(LongFeedbackRepository longFeedbackRepository, ParticipationAuthorizationCheckService participationAuthorizationCheckService) {
        this.longFeedbackRepository = longFeedbackRepository;

        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
    }

    @GetMapping("results/{resultId}/feedbacks/{feedbackId}/long-feedback")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LongFeedbackText> getLongFeedback(@PathVariable Long resultId, @PathVariable Long feedbackId) {
        log.debug("REST request to get long feedback: {} (result: {})", feedbackId, resultId);

        final LongFeedbackText longFeedbackText = longFeedbackRepository.findByIdWithFeedbackAndResultAndParticipationElseThrow(feedbackId);
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
