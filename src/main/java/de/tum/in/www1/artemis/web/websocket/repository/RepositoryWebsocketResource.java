package de.tum.in.www1.artemis.web.websocket.repository;

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

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Controller
@SuppressWarnings("unused")
public class RepositoryWebsocketResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final AuthorizationCheckService authCheckService;

    private final GitService gitService;

    private final UserService userService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final RepositoryService repositoryService;

    private final Optional<VersionControlService> versionControlService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public RepositoryWebsocketResource(UserService userService, AuthorizationCheckService authCheckService, GitService gitService, SimpMessageSendingOperations messagingTemplate,
            RepositoryService repositoryService, Optional<VersionControlService> versionControlService, ExerciseService exerciseService,
            ProgrammingExerciseService programmingExerciseService, ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.messagingTemplate = messagingTemplate;
        this.repositoryService = repositoryService;
        this.versionControlService = versionControlService;
        this.exerciseService = exerciseService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    /**
     * Update a list of files based on the submission's content.
     *
     * @param participationId id of participation to which the files belong
     * @param submissions     information about the file updates
     * @param principal       used to check if the user can update the files
     */
    @MessageMapping("/topic/repository/{participationId}/files")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public void updateParticipationFiles(@DestinationVariable Long participationId, @Payload List<FileSubmission> submissions, Principal principal) {
        // Without this, custom jpa repository methods don't work in a websocket channel.
        SecurityUtils.setAuthorizationObject();

        String topic = "/topic/repository/" + participationId + "/files";
        Participation participation;
        try {
            participation = programmingExerciseParticipationService.findParticipation(participationId);
        }
        catch (EntityNotFoundException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "participationNotFound");
            messagingTemplate.convertAndSendToUser(principal.getName(), topic, error);
            return;
        }
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "notAProgrammingExerciseParticipation");
            messagingTemplate.convertAndSendToUser(principal.getName(), topic, error);
            return;
        }
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;

        // User must have the necessary permissions to update a file.
        // When the buildAndTestAfterDueDate is set, the student can't change the repository content anymore after the due date.
        boolean repositoryIsLocked = programmingExerciseService.isParticipationRepositoryLocked((ProgrammingExerciseParticipation) participation);
        if (repositoryIsLocked || !programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation, principal)) {
            FileSubmissionError error = new FileSubmissionError(participationId, "noPermissions");
            messagingTemplate.convertAndSendToUser(principal.getName(), topic, error);
            return;
        }
        // Git repository must be available to update a file
        Repository repository;
        try {
            repository = gitService.getOrCheckoutRepository(programmingExerciseParticipation);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutConflict");
            messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/repository/" + participationId + "/files", error);
            return;
        }
        catch (GitAPIException | InterruptedException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutFailed");
            messagingTemplate.convertAndSendToUser(principal.getName(), topic, error);
            return;
        }
        Map<String, String> fileSaveResult = saveFileSubmissions(submissions, repository);
        messagingTemplate.convertAndSendToUser(principal.getName(), topic, fileSaveResult);
    }

    /**
     * Update a list of files in a test repository based on the submission's content.
     *
     * @param exerciseId  of exercise to which the files belong
     * @param submissions information about the file updates
     * @param principal   used to check if the user can update the files
     */
    @MessageMapping("/topic/test-repository/{exerciseId}/files")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public void updateTestFiles(@DestinationVariable Long exerciseId, @Payload List<FileSubmission> submissions, Principal principal) {
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();

        ProgrammingExercise exercise = (ProgrammingExercise) exerciseService.findOneWithAdditionalElements(exerciseId);
        String testRepoName = exercise.getProjectKey().toLowerCase() + "-" + RepositoryType.TESTS.getName();
        VcsRepositoryUrl testsRepoUrl = versionControlService.get().getCloneRepositoryUrl(exercise.getProjectKey(), testRepoName);
        String topic = "/topic/test-repository/" + exerciseId + "/files";

        Repository repository;
        try {
            repository = repositoryService.checkoutRepositoryByName(principal, exercise, testsRepoUrl.getURL());
        }
        catch (IllegalAccessException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "noPermissions");
            messagingTemplate.convertAndSendToUser(principal.getName(), topic, error);
            return;
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutConflict");
            messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/test-repository/" + exerciseId + "/files", error);
            return;
        }
        catch (GitAPIException | InterruptedException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutFailed");
            messagingTemplate.convertAndSendToUser(principal.getName(), topic, error);
            return;
        }
        Map<String, String> fileSaveResult = saveFileSubmissions(submissions, repository);
        messagingTemplate.convertAndSendToUser(principal.getName(), topic, fileSaveResult);
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
        HashMap<String, String> fileSaveResult = new HashMap<>();
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
     * @throws IOException
     */
    private void fetchAndUpdateFile(FileSubmission submission, Repository repository) throws IOException {
        Optional<File> file = gitService.getFileByName(repository, submission.getFileName());

        if (file.isEmpty()) {
            throw new IOException("File could not be found.");
        }

        InputStream inputStream = new ByteArrayInputStream(submission.getFileContent().getBytes(StandardCharsets.UTF_8));
        Files.copy(inputStream, file.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

}
