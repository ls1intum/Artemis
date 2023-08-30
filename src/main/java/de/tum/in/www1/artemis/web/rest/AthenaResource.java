package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.TextBlockRef;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaFeedbackSuggestionsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for Athena feedback suggestions.
 */
@RestController
@RequestMapping("api/athena/")
@Profile("athena")
public class AthenaResource {

    private final Logger log = LoggerFactory.getLogger(AthenaResource.class);

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final AuthorizationCheckService authCheckService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    /**
     * The AthenaResource provides an endpoint for the client to fetch feedback suggestions from Athena.
     */
    public AthenaResource(AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService, TextExerciseRepository textExerciseRepository,
            TextSubmissionRepository textSubmissionRepository, AuthorizationCheckService authCheckService) {
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET athena/exercises/:exerciseId/submissions/:submissionId/feedback-suggestions : Get feedback suggestions from Athena
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get feedback suggestions for
     * @return 200 Ok if successful with the corresponding result as body
     */
    @GetMapping("exercises/{exerciseId}/submissions/{submissionId}/feedback-suggestions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<TextBlockRef>> getFeedbackSuggestions(@PathVariable long exerciseId, @PathVariable long submissionId) {
        log.debug("REST call to get feedback suggestions for exercise {}, submission {}", exerciseId, submissionId);

        final var exercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        final var submission = textSubmissionRepository.findByIdElseThrow(submissionId);
        if (submission.getParticipation().getExercise().getId() != exerciseId) {
            log.error("Exercise id {} does not match submission's exercise id {}", exerciseId, submission.getParticipation().getExercise().getId());
            throw new ConflictException("Exercise id does not match submission's exercise id", "Exercise", "exerciseIdDoesNotMatch");
        }
        try {
            List<TextBlockRef> feedbackSuggestions = athenaFeedbackSuggestionsService.getFeedbackSuggestions(exercise, submission);
            return ResponseEntity.ok(feedbackSuggestions);
        }
        catch (NetworkingException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
