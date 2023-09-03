package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.TextBlockRef;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaFeedbackSuggestionsService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaRepositoryExportService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for Athena feedback suggestions.
 */
@RestController
@RequestMapping("api/athena/")
@Profile("athena")
public class AthenaResource {

    private final Logger log = LoggerFactory.getLogger(AthenaResource.class);

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final AuthorizationCheckService authCheckService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private final AthenaRepositoryExportService athenaRepositoryExportService;

    /**
     * The AthenaResource provides an endpoint for the client to fetch feedback suggestions from Athena.
     */
    public AthenaResource(TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository, AuthorizationCheckService authCheckService,
            AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService, AthenaRepositoryExportService athenaRepositoryExportService) {
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.authCheckService = authCheckService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.athenaRepositoryExportService = athenaRepositoryExportService;
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

    /**
     * Check if the given auth header is valid for Athena, otherwise throw an exception.
     *
     * @param auth the auth header value to check
     */
    private void checkAthenaSecret(String auth) {
        if (!auth.equals(athenaSecret)) {
            log.error("Athena secret does not match");
            throw new AccessForbiddenException("Athena secret does not match");
        }
    }

    /**
     * GET athena/exercises/:exerciseId/submissions/:submissionId/repository : Get the repository as a zip file download
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get the repository for
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("exercises/{exerciseId}/submissions/{submissionId}/repository")
    @EnforceNothing // We check the Athena secret instead
    public ResponseEntity<Resource> getRepository(@PathVariable long exerciseId, @PathVariable long submissionId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get student repository for exercise {}, submission {}", exerciseId, submissionId);
        checkAthenaSecret(auth);
        return athenaRepositoryExportService.exportRepository(exerciseId, submissionId, null);
    }

    /**
     * GET athena/exercises/:exerciseId/repository/template : Get the template repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("exercises/{exerciseId}/repository/template")
    @EnforceNothing // We check the Athena secret instead
    public ResponseEntity<Resource> getTemplateRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get template repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.TEMPLATE);
    }

    /**
     * GET athena/exercises/:exerciseId/repository/solution : Get the solution repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("exercises/{exerciseId}/repository/solution")
    @EnforceNothing // We check the Athena secret instead
    public ResponseEntity<Resource> getSolutionRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get solution repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.SOLUTION);
    }

    /**
     * GET athena/exercises/:exerciseId/repository/tests : Get the test repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("exercises/{exerciseId}/repository/tests")
    @EnforceNothing // We check the Athena secret instead
    public ResponseEntity<Resource> getTestRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get test repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.TESTS);
    }
}
