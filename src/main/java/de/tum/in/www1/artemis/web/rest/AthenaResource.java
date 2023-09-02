package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaFeedbackSuggestionsService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

/**
 * REST controller for Athena feedback suggestions.
 */
@RestController
@RequestMapping("api/athena/")
@Profile("athena")
public class AthenaResource {

    private final Logger log = LoggerFactory.getLogger(AthenaResource.class);

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    // We reuse the same directory as the programming exercise export service for this.
    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final AuthorizationCheckService authCheckService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private final FileService fileService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    /**
     * The AthenaResource provides an endpoint for the client to fetch feedback suggestions from Athena.
     */
    public AthenaResource(TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository,
            ProgrammingExerciseExportService programmingExerciseExportService, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService, FileService fileService,
            ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.authCheckService = authCheckService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.fileService = fileService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
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
     * Check if feedback suggestions are enabled for the given exercise, otherwise throw an exception.
     *
     * @param exercise the exercise to check
     */
    private void checkFeedbackSuggestionsEnabledElseThrow(de.tum.in.www1.artemis.domain.Exercise exercise) {
        if (!exercise.getFeedbackSuggestionsEnabled()) {
            log.error("Feedback suggestions are not enabled for exercise {}", exercise.getId());
            throw new AccessForbiddenException("Feedback suggestions are not enabled for exercise");
        }
    }

    /**
     * Export the repository for the given exercise and participation to a zip file.
     * The ZIP file will be deleted automatically after 15 minutes.
     *
     * @param exerciseId     the id of the exercise to export the repository for
     * @param submissionId   the id of the submission to export the repository for (only for student repository, otherwise pass null)
     * @param repositoryType the type of repository to export. Pass null to export the student repository.
     * @return 200 Ok if successful with the corresponding result as body
     * @throws IOException if the export fails
     */
    private ResponseEntity<Resource> exportRepository(long exerciseId, Long submissionId, RepositoryType repositoryType) throws IOException {
        log.debug("Exporting repository for exercise {}, submission {}", exerciseId, submissionId);

        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        checkFeedbackSuggestionsEnabledElseThrow(programmingExercise);

        var exportOptions = new RepositoryExportOptionsDTO();
        exportOptions.setAnonymizeRepository(false);
        exportOptions.setExportAllParticipants(false);
        exportOptions.setFilterLateSubmissions(false);
        exportOptions.setFilterLateSubmissionsIndividualDueDate(false);

        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }

        Path exportDir = fileService.getTemporaryUniquePath(repoDownloadClonePath, 15);
        Path zipFile = null;

        if (repositoryType == null) { // Export student repository
            var submission = programmingSubmissionRepository.findById(submissionId).orElseThrow();
            zipFile = programmingExerciseExportService.createZipForRepositoryWithParticipation(programmingExercise,
                    (ProgrammingExerciseStudentParticipation) submission.getParticipation(), exportOptions, exportDir, exportDir);
        }
        else {
            List<String> exportErrors = List.of();
            var exportFile = programmingExerciseExportService.exportInstructorRepositoryForExercise(programmingExercise.getId(), repositoryType, exportDir, exportErrors);
            if (exportFile.isPresent()) {
                zipFile = exportFile.get().toPath();
            }
        }

        if (zipFile == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseUtil.ok(zipFile.toFile());
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
        return exportRepository(exerciseId, submissionId, null);
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
        return exportRepository(exerciseId, null, RepositoryType.TEMPLATE);
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
        return exportRepository(exerciseId, null, RepositoryType.SOLUTION);
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
        return exportRepository(exerciseId, null, RepositoryType.TESTS);
    }
}
