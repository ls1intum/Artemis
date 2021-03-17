package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
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

    private final ExamSubmissionService examSubmissionService;

    private final BuildLogEntryService buildLogService;

    public RepositoryProgrammingExerciseParticipationResource(UserRepository userRepository, AuthorizationCheckService authCheckService, GitService gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService, RepositoryService repositoryService,
            ProgrammingExerciseParticipationService participationService, ProgrammingExerciseRepository programmingExerciseRepository, ExamSubmissionService examSubmissionService,
            BuildLogEntryService buildLogService) {
        super(userRepository, authCheckService, gitService, continuousIntegrationService, repositoryService, versionControlService, programmingExerciseRepository);
        this.participationService = participationService;
        this.examSubmissionService = examSubmissionService;
        this.buildLogService = buildLogService;
    }

    @Override
    Repository getRepository(Long participationId, RepositoryActionType repositoryAction, boolean pullOnGet) throws InterruptedException, IllegalAccessException, GitAPIException {
        Participation participation = participationService.findParticipation(participationId);
        // Error case 1: The participation is not from a programming exercise.
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        ProgrammingExerciseParticipation programmingParticipation = (ProgrammingExerciseParticipation) participation;
        // Error case 2: The user does not have permissions to push into the repository.
        boolean hasPermissions = participationService.canAccessParticipation(programmingParticipation);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        // Error case 3: The user's participation repository is locked.
        if (repositoryAction == RepositoryActionType.WRITE && programmingParticipation.isLocked()) {
            throw new IllegalAccessException();
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        var programmingExercise = programmingParticipation.getProgrammingExercise();
        boolean isStudent = !authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise);
        // Error case 4: The student can reset the repository only before and a tutor/instructor only after the due date has passed
        if (repositoryAction == RepositoryActionType.RESET) {
            boolean isOwner = true; // true for Solution- and TemplateProgrammingExerciseParticipation
            if (participation instanceof StudentParticipation) {
                isOwner = authCheckService.isOwnerOfParticipation((StudentParticipation) participation);
            }
            if (isStudent && programmingParticipation.isLocked()) {
                throw new IllegalAccessException();
            }
            // A tutor/instructor who is owner of the exercise should always be able to reset the repository
            else if (!isStudent && !isOwner) {
                // Check if a tutor is allowed to reset during the assessment
                // Check for a regular course exercise
                if (!programmingExercise.isExamExercise() && !programmingParticipation.isLocked()) {
                    throw new IllegalAccessException();
                }
                // Check for an exam exercise, as it might not be locked but a student might still be allowed to submit
                var optStudent = ((StudentParticipation) participation).getStudent();
                if (optStudent.isPresent() && programmingExercise.isExamExercise() && examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, optStudent.get())) {
                    throw new IllegalAccessException();
                }
            }
        }
        // Error case 5: The user is not (any longer) allowed to submit to the exam/exercise. This check is only relevant for students.
        // This must be a student participation as hasPermissions would have been false and an error already thrown
        boolean isStudentParticipation = participation instanceof ProgrammingExerciseStudentParticipation;
        if (isStudentParticipation && isStudent && !examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, user)) {
            throw new IllegalAccessException();
        }
        var repositoryUrl = programmingParticipation.getVcsRepositoryUrl();
        return gitService.getOrCheckoutRepository(repositoryUrl, pullOnGet);
    }

    @Override
    VcsRepositoryUrl getRepositoryUrl(Long participationId) throws IllegalArgumentException {
        Participation participation = participationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        return ((ProgrammingExerciseParticipation) participation).getVcsRepositoryUrl();
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

    /**
     * GET /repository/{participationId}/files-change
     *
     * Gets the files of the repository and checks whether they were changed during a student participation with respect to the initial template
     *
     * @param participationId participation of the student
     * @return the ResponseEntity with status 200 (OK) and a map of files with the information if they were changed/are new.
     */
    @GetMapping(value = "/repository/{participationId}/files-change", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Map<String, Boolean>> getFilesWithInformationAboutChange(@PathVariable Long participationId) {
        return super.executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(participationId, RepositoryActionType.READ, true);
            var participation = participationService.findParticipation(participationId);
            var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(participation.getExercise().getId());

            Repository templateRepository = getRepository(exercise.getTemplateParticipation().getId(), RepositoryActionType.READ, true);
            var filesWithInformationAboutChange = super.repositoryService.getFilesWithInformationAboutChange(repository, templateRepository);
            return new ResponseEntity<>(filesWithInformationAboutChange, HttpStatus.OK);
        });
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.getFile(participationId, filename);
    }

    /**
     * GET /repository/{participationId}/files-content
     *
     * Gets the files of the repository with content
     *
     * @param participationId participation of the student/template/solution
     * @return the ResponseEntity with status 200 (OK) and a map of files with their content
     */
    @GetMapping(value = "/repository/{participationId}/files-content", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Map<String, String>> getFilesWithContent(@PathVariable Long participationId) {
        return super.executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(participationId, RepositoryActionType.READ, true);
            var filesWithContent = super.repositoryService.getFilesWithContent(repository);
            return new ResponseEntity<>(filesWithContent, HttpStatus.OK);
        });
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
     * @param commit          whether to commit after updating the files
     * @param principal       used to check if the user can update the files
     * @return {Map<String, String>} file submissions or the appropriate http error
     */
    @PutMapping(value = "/repository/{participationId}/files")
    public ResponseEntity<Map<String, String>> updateParticipationFiles(@PathVariable("participationId") Long participationId, @RequestBody List<FileSubmission> submissions,
            @RequestParam(defaultValue = "false") boolean commit, Principal principal) {
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
        boolean repositoryIsLocked = programmingExerciseParticipation.isLocked();
        if (repositoryIsLocked || !participationService.canAccessParticipation(programmingExerciseParticipation, principal)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }

        // Git repository must be available to update a file
        Repository repository;
        try {
            repository = getRepository(programmingExerciseParticipation.getId(), RepositoryActionType.WRITE, true);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
        catch (GitAPIException | InterruptedException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutFailed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
        catch (IllegalAccessException e) {
            FileSubmissionError error = new FileSubmissionError(participationId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
        // Apply checks for exam (submission is in time & user's student exam has the exercise)
        // Checks only apply to students and tutors, otherwise template, solution and assignment participation can't be edited using the code editor
        User user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExerciseParticipation.getProgrammingExercise())
                && !examSubmissionService.isAllowedToSubmitDuringExam(programmingExerciseParticipation.getProgrammingExercise(), user)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "notAllowedExam");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
        Map<String, String> fileSaveResult = saveFileSubmissions(submissions, repository);

        if (commit) {
            var response = super.commitChanges(participationId);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new ResponseStatusException(response.getStatusCode());
            }
        }

        return ResponseEntity.ok(fileSaveResult);
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
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long participationId) throws GitAPIException, InterruptedException {
        return super.getStatus(participationId);
    }

    /**
     * GET /repository/:participationId/buildlogs : get the build log from Bamboo for the "participationId" repository.
     *
     * @param participationId to identify the repository with.
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/repository/{participationId}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BuildLogEntry>> getBuildLogs(@PathVariable Long participationId) {
        log.debug("REST request to get build log : {}", participationId);

        ProgrammingExerciseParticipation participation = participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(participationId);

        if (!participationService.canAccessParticipation(participation)) {
            return forbidden();
        }

        Optional<Submission> optionalSubmission = participation.getSubmissions().stream().findFirst();
        if (optionalSubmission.isEmpty()) {
            // Can't return build logs if a submission doesn't exist yet
            return ResponseEntity.ok(new ArrayList<>());
        }

        ProgrammingSubmission latestSubmission = (ProgrammingSubmission) optionalSubmission.get();
        // Do not return build logs if the build hasn't failed
        if (!latestSubmission.isBuildFailed()) {
            return forbidden();
        }

        // Load the logs from the database
        List<BuildLogEntry> buildLogsFromDatabase = buildLogService.getLatestBuildLogs(latestSubmission);

        // If there are logs present in the database, return them (they were already filtered when inserted)
        if (!buildLogsFromDatabase.isEmpty()) {
            return new ResponseEntity<>(buildLogsFromDatabase, HttpStatus.OK);
        }

        // Otherwise attempt to fetch the build logs from the CI
        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(latestSubmission);

        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}
