package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisProgrammingExerciseDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.exercise.PyrisExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisExerciseChatSessionService extends AbstractIrisChatSessionService<IrisExerciseChatSession> implements IrisRateLimitedFeatureInterface {

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final PyrisProgrammingExerciseDTOService pyrisProgrammingExerciseDTOService;

    public IrisExerciseChatSessionService(IrisMessageService irisMessageService, LLMTokenUsageService llmTokenUsageService, IrisSettingsService irisSettingsService,
            IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, ProgrammingExerciseRepository programmingExerciseRepository,
            ObjectMapper objectMapper, PyrisProgrammingExerciseDTOService pyrisProgrammingExerciseDTOService) {
        super(irisSessionRepository, objectMapper, irisMessageService, irisChatWebsocketService, llmTokenUsageService);
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.pyrisProgrammingExerciseDTOService = pyrisProgrammingExerciseDTOService;
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    public IrisExerciseChatSession createChatSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return irisSessionRepository.save(new IrisExerciseChatSession(exercise, user));
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisExerciseChatSession session) {
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, session.getExercise(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisExerciseChatSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, session.getExercise());
    }

    @Override
    public void sendOverWebsocket(IrisExerciseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param irisSession The chat session to send to the LLM. The full session is loaded from the database by ID.
     */
    @Override
    public void requestAndHandleResponse(IrisExerciseChatSession irisSession) {
        var session = (IrisExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        if (session.getExercise().isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }

        String variant = irisSettingsService.getCombinedIrisSettingsFor(session.getExercise(), false).irisChatSettings().selectedVariant();
        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getExercise().getId());
        var latestSubmissionDTO = getLatestSubmissionIfExists(exercise, session.getUser()).map(pyrisProgrammingExerciseDTOService::convertSubmission).orElse(null);
        var exerciseDTO = pyrisProgrammingExerciseDTOService.convertExercise(exercise);
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var courseDTO = PyrisCourseDTO.from(course);
        var conversationDTO = session.getMessages().stream().map(PyrisMessageDTO::from).toList();
        var userDTO = PyrisUserDTO.from(session.getUser());

        // @formatter:off
        pyrisPipelineService.executePipeline(
                "tutor-chat", // TODO: Rename this to 'programming-exercise-chat'
                variant,
                token -> new ExerciseChatJob(token, course.getId(), exercise.getId(), session.getId()),
                executionDto -> new PyrisExerciseChatPipelineExecutionDTO(
                        latestSubmissionDTO,
                        exerciseDTO,
                        courseDTO,
                        conversationDTO,
                        userDTO,
                        executionDto.settings(),
                        executionDto.initialStages()
                ),
                stages -> irisChatWebsocketService.sendStatusUpdate(session, stages)
        );
        // @formatter:on
    }

    private Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        var participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(sub.getId()));
    }

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisExerciseChatSession session) {
        var exercise = session.getExercise();
        builder.withCourse(exercise.getCourseViaExerciseGroupOrCourseMember().getId()).withExercise(exercise.getId());
    }
}
