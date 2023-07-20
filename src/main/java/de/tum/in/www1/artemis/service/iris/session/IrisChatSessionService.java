package de.tum.in.www1.artemis.service.iris.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import javax.ws.rs.BadRequestException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
@Profile("iris")
public class IrisChatSessionService implements IrisSessionSubServiceInterface {

    private final Logger log = LoggerFactory.getLogger(IrisChatSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisWebsocketService irisWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    public IrisChatSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisWebsocketService irisWebsocketService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository, GitService gitService,
            RepositoryService repositoryService, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisWebsocketService = irisWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param session The session to check
     * @param user    The user to check
     */
    @Override
    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        var chatSession = castToSessionType(session, IrisChatSession.class);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, chatSession.getExercise(), user);
        if (!Objects.equals(chatSession.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsIrisActivated(IrisSession session) {
        var chatSession = castToSessionType(session, IrisChatSession.class);
        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(chatSession.getExercise());
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisSession session) {
        var fullSession = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        Map<String, Object> parameters = new HashMap<>();
        if (!(fullSession instanceof IrisChatSession chatSession)) {
            throw new BadRequestException("Trying to get Iris response for session " + session.getId() + " without exercise");
        }
        var exercise = chatSession.getExercise();
        parameters.put("exercise", exercise);
        parameters.put("course", exercise.getCourseViaExerciseGroupOrCourseMember());
        parameters.put("latestSubmission", "");
        parameters.put("buildFailed", "");
        parameters.put("buildLog", "");
        var participation = programmingExerciseStudentParticipationRepository.findWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), chatSession.getUser().getLogin());
        if (participation.isPresent()) {
            var submission = participation.get().getSubmissions().stream().max(Submission::compareTo);
            Optional<ProgrammingSubmission> latestSubmission = Optional.empty();
            if (submission.isPresent()) {
                latestSubmission = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(submission.get().getId());
            }
            if (latestSubmission.isPresent()) {
                parameters.put("latestSubmission", latestSubmission.get());
                parameters.put("buildFailed", latestSubmission.get().isBuildFailed());
                parameters.put("buildLog", latestSubmission.get().getBuildLogEntries());
            }
        }
        parameters.put("session", fullSession);
        addDiffAndTemplatesForStudentAndExerciseIfPossible(chatSession.getUser(), exercise, parameters);

        var irisSettings = irisSettingsService.getCombinedIrisSettings(exercise, false);
        irisConnectorService.sendRequest(irisSettings.getIrisChatSettings().getTemplate(), irisSettings.getIrisChatSettings().getPreferredModel(), parameters)
                .handleAsync((irisMessage, throwable) -> {
                    if (throwable != null) {
                        log.error("Error while getting response from Iris model", throwable);
                        irisWebsocketService.sendException(fullSession, throwable.getCause());
                    }
                    else if (irisMessage != null) {
                        var irisMessageSaved = irisMessageService.saveMessage(irisMessage.message(), fullSession, IrisMessageSender.LLM);
                        irisWebsocketService.sendMessage(irisMessageSaved);
                    }
                    else {
                        log.error("No response from Iris model");
                        irisWebsocketService.sendException(fullSession, new IrisNoResponseException());
                    }
                    return null;
                });
    }

    private void addDiffAndTemplatesForStudentAndExerciseIfPossible(User student, ProgrammingExercise exercise, Map<String, Object> parameters) {
        parameters.put("gitDiff", "");
        parameters.put("studentRepository", Map.of());
        parameters.put("templateRepository", Map.of());

        var studentParticipation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), student.getLogin());
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId());

        Repository templateRepo;
        Repository studentRepo;

        if (templateParticipation.isEmpty()) {
            throw new InternalServerErrorException("Iris cannot function without template participation");
        }
        if (studentParticipation.isEmpty()) {
            try {
                templateRepo = gitService.getOrCheckoutRepository(templateParticipation.get().getVcsRepositoryUrl(), true);
            }
            catch (GitAPIException e) {
                throw new InternalServerErrorException("Iris cannot function without template participation");
            }
            parameters.put("templateRepository", repositoryService.getFilesWithContent(templateRepo));
            return;
        }

        try {
            templateRepo = gitService.getOrCheckoutRepository(templateParticipation.get().getVcsRepositoryUrl(), true);
            studentRepo = gitService.getOrCheckoutRepository(studentParticipation.get().getVcsRepositoryUrl(), true);
        }
        catch (GitAPIException e) {
            throw new InternalServerErrorException("Could not fetch existing student or template participation");
        }
        parameters.put("templateRepository", repositoryService.getFilesWithContent(templateRepo));
        parameters.put("studentRepository", repositoryService.getFilesWithContent(studentRepo));

        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(studentRepo);

        gitService.resetToOriginHead(templateRepo);
        gitService.pullIgnoreConflicts(templateRepo);
        gitService.resetToOriginHead(studentRepo);
        gitService.pullIgnoreConflicts(studentRepo);

        try (ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream(); Git git = Git.wrap(templateRepo)) {
            git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setOutputStream(diffOutputStream).call();
            parameters.put("gitDiff", diffOutputStream.toString());
        }
        catch (GitAPIException | IOException e) {
            throw new InternalServerErrorException("Could not generate diff from existing template and student participation");
        }
    }
}
