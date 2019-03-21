package de.tum.in.www1.artemis.web.websocket.repository;

import afu.org.checkerframework.checker.nullness.qual.Nullable;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.Optional;


@Controller
@SuppressWarnings("unused")
public class RepositoryWebsocketService {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final ParticipationService participationService;
    private final AuthorizationCheckService authCheckService;

    private final Optional<GitService> gitService;
    private final UserService userService;
    private final SimpMessageSendingOperations messagingTemplate;

    public RepositoryWebsocketService(UserService userService,
                                      ParticipationService participationService,
                                      AuthorizationCheckService authCheckService,
                                      Optional<GitService> gitService,
                                      SimpMessageSendingOperations messagingTemplate) {
        this.userService = userService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.gitService = gitService;
        this.messagingTemplate = messagingTemplate;
    }


    @Nullable
    private boolean checkParticipation(Participation participation, Principal principal) {
        if (!userHasPermissions(participation, principal)) return false;
        return Optional.ofNullable(participation).isPresent();
    }

    private boolean userHasPermissions(Participation participation, Principal principal) {
        if (!authCheckService.isOwnerOfParticipation(participation, principal)) {
            //if the user is not the owner of the participation, the user can only see it in case he is
            //a teaching assistant or an instructor of the course, or in case he is admin
            User user = userService.getUserWithGroupsAndAuthorities(principal);
            Course course = participation.getExercise().getCourse();
            return authCheckService.isTeachingAssistantInCourse(course, user) ||
                authCheckService.isInstructorInCourse(course, user) ||
                authCheckService.isAdmin();
        }
        return true;
    }

    /**
     * PUT /repository/{participationId}/file: Update the file content
     *
     * @param participationId Participation ID
     * @param submission
     * @param principal
     * @return
     */
    @MessageMapping("/topic/repository/{participationId}/file")
    public void updateFile(@DestinationVariable Long participationId, @Payload FileSubmission submission, Principal principal) {
        Participation participation = participationService.findOne(participationId);
        if (checkParticipation(participation, principal)) {
            Repository repository;
            Optional<File> file;

            try {
                repository = gitService.get().getOrCheckoutRepository(participation);
                file = gitService.get().getFileByName(repository, submission.getFileName());
            } catch (IOException | InterruptedException ex) {
                messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/repository/" + participationId + "/file", ex.getMessage());
                return;
            }

            if(!file.isPresent()) {
                FileSubmissionError error = new FileSubmissionError(participationId, submission.getFileName(), "File could not be found.");
                messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/repository/" + participationId + "/file", error);
                return;
            }

            try {
                InputStream inputStream = new ByteArrayInputStream(submission.getFileContent().getBytes(StandardCharsets.UTF_8));
                Files.copy(inputStream, file.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch(IOException ex) {
                messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/repository/" + participationId + "/file", ex.getMessage());
                return;
            }

            FileSubmissionSuccess successMessage = new FileSubmissionSuccess(submission.getFileName());
            messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/repository/" + participationId + "/file", successMessage);

        } else {
            FileSubmissionError error = new FileSubmissionError(participationId, submission.getFileName(), "User does not have the necessary permissions.");
            messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/repository/" + participationId + "/file", error);
        }
    }

}
