package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Executes repository actions on repositories related to the participation id transmitted. Available to the owner of the participation, TAs/Instructors of the exercise and Admins.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class RepositoryProgrammingExerciseParticipationResource extends RepositoryResource {

    private final ProgrammingExerciseParticipationService participationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final Optional<VersionControlService> versionControlService;

    private final ExamSubmissionService examSubmissionService;

    public RepositoryProgrammingExerciseParticipationResource(UserService userService, AuthorizationCheckService authCheckService, GitService gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ProgrammingExerciseService programmingExerciseService, ExamSubmissionService examSubmissionService) {
        super(userService, authCheckService, gitService, continuousIntegrationService, repositoryService);
        this.participationService = participationService;
        this.programmingExerciseService = programmingExerciseService;
        this.examSubmissionService = examSubmissionService;
        this.versionControlService = versionControlService;
    }

    @Override
    Repository getRepository(Long participationId, RepositoryActionType repositoryAction, boolean pullOnGet) throws InterruptedException, IllegalAccessException, GitAPIException {
        Participation participation = participationService.findParticipation(participationId);
        // Error case 1: The participation is not from a programming exercise.
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        // Error case 2: The user does not have permissions to push into the repository.
        boolean hasPermissions = participationService.canAccessParticipation((ProgrammingExerciseParticipation) participation);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        // Error case 3: The user's participation repository is locked.
        if (repositoryAction == RepositoryActionType.WRITE && programmingExerciseService.isParticipationRepositoryLocked((ProgrammingExerciseParticipation) participation)) {
            throw new IllegalAccessException();
        }
        // Error case 4: The user is not (any longer) allowed to submit to the exam/exercise. This check is only relevant for students.
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise()) && !examSubmissionService.isAllowedToSubmit(participation.getExercise(), user)) {
            throw new IllegalAccessException();
        }
        URL repositoryUrl = ((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl();
        return gitService.getOrCheckoutRepository(repositoryUrl, pullOnGet);
    }

    @Override
    URL getRepositoryUrl(Long participationId) throws IllegalArgumentException {
        Participation participation = participationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        return ((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl();
    }

    @Override
    boolean canAccessRepository(Long participationId) throws IllegalArgumentException {
        Participation participation = participationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        return participationService.canAccessParticipation((ProgrammingExerciseParticipation) participation);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, FileType>> getFiles(@PathVariable Long participationId) {
        return super.getFiles(participationId);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.getFile(participationId, filename);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> createFile(@PathVariable Long participationId, @RequestParam("file") String filename, HttpServletRequest request) {
        return super.createFile(participationId, filename, request);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> createFolder(@PathVariable Long participationId, @RequestParam("folder") String folderName, HttpServletRequest request) {
        return super.createFolder(participationId, folderName, request);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> renameFile(@PathVariable Long participationId, @RequestBody FileMove fileMove) {
        return super.renameFile(participationId, fileMove);
    }

    @Override
    @DeleteMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.deleteFile(participationId, filename);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> pullChanges(@PathVariable Long participationId) {
        return super.pullChanges(participationId);
    }

    /**
     * Update a list of files based on the submission's content.
     *
     * @param participationId id of participation to which the files belong
     * @param submissions     information about the file updates
     * @param principal       used to check if the user can update the files
     * @return {Map<String, String>} file submissions or the appropriate http error
     */
    @PutMapping(value = "/repository/{participationId}/files")
    public ResponseEntity<Map<String, String>> updateParticipationFiles(@PathVariable("participationId") Long participationId, @RequestBody List<FileSubmission> submissions,
            Principal principal) {
        Participation participation;
        try {
            participation = participationService.findParticipation(participationId);
        }
        catch (EntityNotFoundException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "participationNotFound");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        }
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "notAProgrammingExerciseParticipation");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
        final ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;

        // User must have the necessary permissions to update a file.
        // When the buildAndTestAfterDueDate is set, the student can't change the repository content anymore after the due date.
        boolean repositoryIsLocked = programmingExerciseService.isParticipationRepositoryLocked((ProgrammingExerciseParticipation) participation);
        if (repositoryIsLocked || !participationService.canAccessParticipation(programmingExerciseParticipation, principal)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }

        // Git repository must be available to update a file
        Repository repository;
        try {
            repository = gitService.getOrCheckoutRepository(programmingExerciseParticipation);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
        catch (GitAPIException | InterruptedException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutFailed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
        // Apply checks for exam (submission is in time & user's student exam has the exercise)
        // Checks only apply to StudentParticipation, otherwise template and solution participation can't be edited using the code editor
        User user = userService.getUserWithGroupsAndAuthorities(principal.getName());
        if (participation instanceof ProgrammingExerciseStudentParticipation
                && !examSubmissionService.isAllowedToSubmit(programmingExerciseParticipation.getProgrammingExercise(), user)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "notAllowedExam");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
        Map<String, String> fileSaveResult = saveFileSubmissions(submissions, repository);
        return ResponseEntity.ok(fileSaveResult);
    }

    /**
     * Update a list of files in a test repository based on the submission's content.
     *
     * @param exerciseId  of exercise to which the files belong
     * @param submissions information about the file updates
     * @param principal   used to check if the user can update the files
     * @return {Map<String, String>} file submissions or the appropriate http error
     */
    @PutMapping(value = "/test-repository/" + "{exerciseId}" + "/files")
    public ResponseEntity<Map<String, String>> updateTestFiles(@PathVariable("exerciseId") Long exerciseId, @RequestBody List<FileSubmission> submissions, Principal principal) {
        ProgrammingExercise exercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        String testRepoName = exercise.getProjectKey().toLowerCase() + "-" + RepositoryType.TESTS.getName();
        if (versionControlService.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VCSNotPresent");
        }
        VcsRepositoryUrl testsRepoUrl = versionControlService.get().getCloneRepositoryUrl(exercise.getProjectKey(), testRepoName);

        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByName(principal, exercise, testsRepoUrl.getURL());
        }
        catch (IllegalAccessException e) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
        catch (GitAPIException | InterruptedException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutFailed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
        Map<String, String> fileSaveResult = saveFileSubmissions(submissions, repository);
        return ResponseEntity.ok(fileSaveResult);
    }

    /**
     * Iterate through the file submissions and try to save each one. Will continue iterating when an error is encountered on updating a file and store it's error in the resulting
     * Map.
     *
     * @param submissions the file submissions (changes) that should be saved in the repository
     * @param repository the git repository in which the file changes should be saved
     * @return a map of <filename, error | null>
     */
    private Map<String, String> saveFileSubmissions(List<FileSubmission> submissions, Repository repository) {
        // If updating the file fails due to an IOException, we send an error message for the specific file and try to update the rest
        Map<String, String> fileSaveResult = new HashMap<>();
        submissions.forEach((submission) -> {
            try {
                fetchAndUpdateFile(submission, repository);
                fileSaveResult.put(submission.getFileName(), null);
            }
            catch (IOException ex) {
                fileSaveResult.put(submission.getFileName(), ex.getMessage());
            }
        });
        return fileSaveResult;
    }

    /**
     * Retrieve the file from repository and update its content with the submission's content. Throws exceptions if the user doesn't have permissions, the file can't be retrieved
     * or it can't be updated.
     *
     * @param submission information about file update
     * @param repository repository in which to fetch and update the file
     * @throws IOException exception when the file in the file submission parameter is empty
     */
    private void fetchAndUpdateFile(FileSubmission submission, Repository repository) throws IOException {
        Optional<File> file = gitService.getFileByName(repository, submission.getFileName());

        if (file.isEmpty()) {
            throw new IOException("File could not be found.");
        }

        InputStream inputStream = new ByteArrayInputStream(submission.getFileContent().getBytes(StandardCharsets.UTF_8));
        Files.copy(inputStream, file.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();
    }

    /**
     * Commit and push the changes to the remote VCS repo.
     * Won't allow a commit if the repository is locked!
     *
     * @param participationId identifier for the repository.
     * @return ok (200) if the push was successful, notFound (404) if the participation does not exist and forbidden (403) if the user does not have permissions to access the participation OR the buildAndTestAfterDueDate is set and the repository is now locked.
     */
    @Override
    @PostMapping(value = "/repository/{participationId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> commitChanges(@PathVariable Long participationId) {
        return super.commitChanges(participationId);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> resetToLastCommit(@PathVariable Long participationId) {
        return super.resetToLastCommit(participationId);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long participationId) throws IOException, GitAPIException, InterruptedException {
        return super.getStatus(participationId);
    }

    /**
     * GET /repository/:participationId/buildlogs : get the build log from Bamboo for the "participationId" repository.
     *
     * @param participationId to identify the repository with.
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/repository/{participationId}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getResultDetails(@PathVariable Long participationId) {
        log.debug("REST request to get build log : {}", participationId);

        ProgrammingExerciseParticipation participation = participationService.findProgrammingExerciseParticipationWithLatestResultAndFeedbacks(participationId);

        if (!participationService.canAccessParticipation(participation)) {
            return forbidden();
        }

        Optional<Result> latestResult = participation.getResults().stream().findFirst();
        // We don't try to fetch build logs for manual results (they were not created through the build but manually by an assessor)!
        if (latestResult.isPresent() && latestResult.get().getAssessmentType().equals(AssessmentType.MANUAL)) {
            // Don't throw an error here, just return an empty list.
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());

        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}
