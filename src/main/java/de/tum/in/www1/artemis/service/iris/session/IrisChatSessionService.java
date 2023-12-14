package de.tum.in.www1.artemis.service.iris.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import javax.ws.rs.BadRequestException;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.dto.iris.chat.IrisChatRequestDTO;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
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

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final IrisRateLimitService rateLimitService;

    public IrisChatSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository, GitService gitService,
            RepositoryService repositoryService, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.rateLimitService = rateLimitService;
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
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, chatSession.getExercise());
    }

    @Override
    public void sendOverWebsocket(IrisMessage message) {
        irisChatWebsocketService.sendMessage(message);
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
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
        if (!(fullSession instanceof IrisChatSession chatSession)) {
            throw new BadRequestException("Trying to get Iris response for session " + session.getId() + " without exercise");
        }
        if (chatSession.getExercise().isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }

        ProgrammingExercise exercise = chatSession.getExercise();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        ProgrammingSubmission latestSubmission = getLatestSubmissionIfExists(exercise, chatSession.getUser());
        boolean buildFailed = latestSubmission != null && latestSubmission.isBuildFailed();
        List<BuildLogEntry> buildLog = latestSubmission != null ? latestSubmission.getBuildLogEntries() : List.of();
        Repository templateRepository = templateRepository(exercise);
        Optional<Repository> studentRepository = studentRepository(latestSubmission);
        Map<String, String> templateRepositoryContents = repositoryService.getFilesWithContent(templateRepository);
        Map<String, String> studentRepositoryContents = studentRepository.map(repositoryService::getFilesWithContent).orElse(Map.of());
        String gitDiff = studentRepository.map(repo -> getGitDiff(templateRepository, repo)).orElse("");

        var irisSettings = irisSettingsService.getCombinedIrisSettingsFor(exercise, false);
        String template = irisSettings.irisChatSettings().getTemplate().getContent();
        String preferredModel = irisSettings.irisChatSettings().getPreferredModel();
        var dto = new IrisChatRequestDTO(exercise, course, latestSubmission, buildFailed, buildLog, chatSession, gitDiff, templateRepositoryContents, studentRepositoryContents);
        irisConnectorService.sendRequestV2(template, preferredModel, dto).handleAsync((response, throwable) -> {
            if (throwable != null) {
                log.error("Error while getting response from Iris model", throwable);
                irisChatWebsocketService.sendException(chatSession, throwable.getCause());
            }
            else if (response != null && response.content().hasNonNull("response")) {
                String responseText = response.content().get("response").asText();
                IrisMessage responseMessage = new IrisMessage();
                responseMessage.addContent(new IrisTextMessageContent(responseText));
                var irisMessageSaved = irisMessageService.saveMessage(responseMessage, chatSession, IrisMessageSender.LLM);
                irisChatWebsocketService.sendMessage(irisMessageSaved);
            }
            else {
                log.error("No response from Iris model");
                irisChatWebsocketService.sendException(chatSession, new IrisNoResponseException());
            }
            return null;
        });
    }

    private ProgrammingSubmission getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        var participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        if (participations.isEmpty()) {
            return null;
        }
        return participations.get(participations.size() - 1).getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerBuildLogEntriesById(sub.getId())).orElse(null);
    }

    private Repository templateRepository(ProgrammingExercise exercise) {
        return templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).map(participation -> {
            try {
                return gitService.getOrCheckoutRepository(participation.getVcsRepositoryUrl(), true);
            }
            catch (GitAPIException e) {
                return null;
            }
        }).orElseThrow(() -> new InternalServerErrorException("Iris cannot function without template participation"));
    }

    private Optional<Repository> studentRepository(@Nullable ProgrammingSubmission latestSubmission) {
        return Optional.ofNullable(latestSubmission).map(sub -> (ProgrammingExerciseParticipation) sub.getParticipation()).map(participation -> {
            try {
                return gitService.getOrCheckoutRepository(participation.getVcsRepositoryUrl(), true);
            }
            catch (GitAPIException e) {
                log.error("Could not fetch existing student participation repository", e);
                return null;
            }
        });
    }

    private String getGitDiff(Repository from, Repository to) {
        var oldTreeParser = new FileTreeIterator(from);
        var newTreeParser = new FileTreeIterator(to);

        gitService.resetToOriginHead(from);
        gitService.pullIgnoreConflicts(from);
        gitService.resetToOriginHead(to);
        gitService.pullIgnoreConflicts(to);

        try (ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream(); Git git = Git.wrap(from)) {
            git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setOutputStream(diffOutputStream).call();
            return diffOutputStream.toString();
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not generate diff from existing template and student participation", e);
            return "";
        }
    }
}
