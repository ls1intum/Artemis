package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@RestController
@RequestMapping("/api")
public class ProgrammingExerciseParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationResource.class);

    private ParticipationService participationService;

    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private ResultService resultService;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ParticipationService participationService,
            ResultService resultService) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.participationService = participationService;
        this.resultService = resultService;
    }

    /**
     * Get the given student participation with its latest result and feedbacks.
     *
     * @return the ResponseEntity with status 200 (OK) and the participation with its latest result in the body.
     */
    @GetMapping(value = "/programming-exercises-participation/{participationId}/student-participation-with-latest-result-and-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipationWithLatestResultForStudentParticipation(@PathVariable Long participationId) {
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseParticipationService
                .findStudentParticipationWithLatestResultAndFeedbacks(participationId);
        if (!participation.isPresent()) {
            return notFound();
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(participation.get())) {
            return forbidden();
        }
        return ResponseEntity.ok(participation.get());
    }

    /**
     * Get the latest result for a given programming exercise participation including it's result.
     *
     * @return the ResponseEntity with status 200 (OK) and the latest result with feedbacks in its body.
     */
    @GetMapping(value = "/programming-exercises-participation/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacksForSolutionParticipation(@PathVariable Long participationId) {
        Participation participation;
        try {
            participation = participationService.findOne(participationId);
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        if (participation instanceof ProgrammingExerciseParticipation) {
            return getLatestResultWithFeedbacks((ProgrammingExerciseParticipation) participation);
        }
        else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Util method for retrieving the latest result with feedbacks of participation. Generates the appropriate response type (ok, forbidden, notFound).
     *
     * @param participation
     * @return the appropriate ResponseEntity for the result request.
     */
    private ResponseEntity<Result> getLatestResultWithFeedbacks(ProgrammingExerciseParticipation participation) {
        if (!programmingExerciseParticipationService.canAccessParticipation(participation)) {
            return forbidden();
        }
        Result result;
        try {
            result = resultService.findLatestResultWithFeedbacksForParticipation(participation.getId());
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        return ResponseEntity.ok(result);
    }
}
