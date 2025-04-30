package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service for managing Iris tutor suggestion sessions.
 * <p>
 * This service is responsible for handling the business logic of Iris tutor suggestion sessions.
 * </p>
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisTutorSuggestionSessionService extends AbstractIrisChatSessionService<IrisTutorSuggestionSession> implements IrisRateLimitedFeatureInterface {

    private static final Logger log = LoggerFactory.getLogger(IrisTutorSuggestionSessionService.class);

    private final IrisSessionRepository irisSessionRepository;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSettingsService irisSettingsService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final PyrisDTOService pyrisDTOService;

    public IrisTutorSuggestionSessionService(IrisSessionRepository irisSessionRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService, IrisRateLimitService rateLimitService,
            PyrisPipelineService pyrisPipelineService, AuthorizationCheckService authCheckService, IrisSettingsService irisSettingsService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, PyrisDTOService pyrisDTOService) {
        super(irisSessionRepository, objectMapper, irisMessageService, irisChatWebsocketService, llmTokenUsageService);
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.authCheckService = authCheckService;
        this.irisSettingsService = irisSettingsService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.pyrisDTOService = pyrisDTOService;
    }

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisTutorSuggestionSession session) {
        builder.withCourse(session.getPost().getCoursePostingBelongsTo().getId());
    }

    @Override
    public void sendOverWebsocket(IrisTutorSuggestionSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void requestAndHandleResponse(IrisTutorSuggestionSession irisSession) {
        requestAndHandleResponse(irisSession, Optional.empty());
    }

    /**
     * Requests and handles the response from the Pyris pipeline for the given session.
     *
     * @param session The IrisTutorSuggestionSession to handle
     * @param event   Optional event to pass to the Pyris pipeline
     */
    public void requestAndHandleResponse(IrisTutorSuggestionSession session, Optional<String> event) {
        var chatSession = (IrisTutorSuggestionSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());

        var variant = "default";

        var post = chatSession.getPost();

        var course = post.getCoursePostingBelongsTo();
        if (course == null) {
            throw new IllegalStateException("Course not found for session " + chatSession.getId());
        }

        var conversation = post.getConversation();
        if (conversation == null) {
            throw new IllegalStateException("Conversation not found for session " + chatSession.getId());
        }

        if (conversation instanceof Channel channel) {
            Optional<Long> lectureIdOptional = Optional.empty();
            Optional<PyrisTextExerciseDTO> textExerciseDTOOptional = Optional.empty();
            Optional<PyrisSubmissionDTO> submissionDTOOptional = Optional.empty();
            Optional<PyrisProgrammingExerciseDTO> programmingExerciseDTOOptional = Optional.empty();

            var lecture = channel.getLecture();
            if (lecture != null) {
                lectureIdOptional = Optional.of(lecture.getId());
            }
            var exercise = channel.getExercise();
            if (exercise != null) {
                switch (exercise.getExerciseType()) {
                    case PROGRAMMING -> {
                        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                        var latestSubmission = getLatestSubmissionIfExists(programmingExercise, chatSession.getUser());
                        PyrisSubmissionDTO pyrisSubmissionDTO = latestSubmission.map(pyrisDTOService::toPyrisSubmissionDTO).orElse(null);
                        PyrisProgrammingExerciseDTO pyrisProgrammingExerciseDTO = pyrisDTOService.toPyrisProgrammingExerciseDTO(programmingExercise);
                        if (pyrisSubmissionDTO != null) {
                            submissionDTOOptional = Optional.of(pyrisSubmissionDTO);
                        }
                        programmingExerciseDTOOptional = Optional.of(pyrisProgrammingExerciseDTO);
                    }
                    case TEXT -> {
                        TextExercise textExercise = (TextExercise) exercise;
                        textExerciseDTOOptional = Optional.of(PyrisTextExerciseDTO.ofWithExampleSolution(textExercise));
                    }
                }
            }
            pyrisPipelineService.executeTutorSuggestionPipeline(variant, chatSession, event, lectureIdOptional, textExerciseDTOOptional, submissionDTOOptional,
                    programmingExerciseDTOOptional);
        }
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }

    @Override
    public void checkHasAccessTo(User user, IrisTutorSuggestionSession irisSession) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, irisSession.getPost().getCoursePostingBelongsTo(), user);
        if (!Objects.equals(irisSession.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", irisSession.getId());
        }
    }

    @Override
    public void checkIsFeatureActivatedFor(IrisTutorSuggestionSession irisSession) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.TUTOR_SUGGESTION, irisSession.getPost().getCoursePostingBelongsTo());
    }

    private Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        List<ProgrammingExerciseStudentParticipation> participations;
        if (exercise.isTeamMode()) {
            participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionByExerciseIdAndStudentLoginInTeam(exercise.getId(), user.getLogin());
        }
        else {
            participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        }

        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(sub.getId()));
    }
}
