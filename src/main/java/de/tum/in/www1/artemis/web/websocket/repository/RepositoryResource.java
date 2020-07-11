package de.tum.in.www1.artemis.web.websocket.repository;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class RepositoryResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final AuthorizationCheckService authCheckService;

    private final GitService gitService;

    private final UserService userService;

    private final RepositoryService repositoryService;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ExamSubmissionService examSubmissionService;

    public RepositoryResource(UserService userService, AuthorizationCheckService authCheckService, GitService gitService, RepositoryService repositoryService, Optional<VersionControlService> versionControlService,
                              ProgrammingExerciseService programmingExerciseService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
                              ExamSubmissionService examSubmissionService) {
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.versionControlService = versionControlService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.examSubmissionService = examSubmissionService;
    }

    /**
     * Update a list of files based on the submission's content.
     *
     * @param participationId id of participation to which the files belong
     * @param submissions     information about the file updates
     * @param principal       used to check if the user can update the files
     */
    @PutMapping(value = Constants.REPOSITORY_RESOURCE_PATH + "{participationId}" + "/files")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Map<String, String>> updateParticipationFiles(@PathVariable("participationId") Long participationId, @RequestBody List<FileSubmission> submissions, Principal principal) {
        Participation participation;
        try {
            participation = programmingExerciseParticipationService.findParticipation(participationId);
        } catch (EntityNotFoundException ex) {
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
        if (repositoryIsLocked || !programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation, principal)) {
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
        User user = userService.getUserWithGroupsAndAuthorities(principal.getName());
        if (!examSubmissionService.isAllowedToSubmit(programmingExerciseParticipation.getProgrammingExercise(), user)) {
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
     */
    @PutMapping(value = "/test-repository/" + "{exerciseId}" + "/files")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Map<String, String>> updateTestFiles(@PathVariable("exerciseId") Long exerciseId, @RequestBody List<FileSubmission> submissions, Principal principal) {
        // TODO: this should rather be a REST call so that security checks are easier to implement. Ask Sascha what he meant
        ProgrammingExercise exercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        String testRepoName = exercise.getProjectKey().toLowerCase() + "-" + RepositoryType.TESTS.getName();
        if(versionControlService.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VCSNotPresent");
        }
        VcsRepositoryUrl testsRepoUrl = versionControlService.get().getCloneRepositoryUrl(exercise.getProjectKey(), testRepoName);

        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByName(principal, exercise, testsRepoUrl.getURL());
        } catch (IllegalAccessException e) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        } catch (GitAPIException | InterruptedException ex) {
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
}
