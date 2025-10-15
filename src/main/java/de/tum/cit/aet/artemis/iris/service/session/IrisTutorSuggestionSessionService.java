package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
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
@Lazy
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

    private final PostRepository postRepository;

    private final IrisMessageService irisMessageService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    public IrisTutorSuggestionSessionService(IrisSessionRepository irisSessionRepository, IrisMessageRepository irisMessageRepository, ObjectMapper objectMapper,
            IrisMessageService irisMessageService, IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, AuthorizationCheckService authCheckService, IrisSettingsService irisSettingsService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, PyrisDTOService pyrisDTOService, PostRepository postRepository, UserRepository userRepository) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService);
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
        this.postRepository = postRepository;
        this.irisMessageService = irisMessageService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisTutorSuggestionSession session) {
        var post = postRepository.findPostOrMessagePostByIdElseThrow(session.getPostId());
        builder.withCourse(post.getCoursePostingBelongsTo().getId());
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
        var post = postRepository.findPostOrMessagePostByIdElseThrow(session.getPostId());
        var postDTO = new PostDTO(post, null);

        var course = post.getCoursePostingBelongsTo();
        if (course == null) {
            throw new IllegalStateException("Course not found for session " + chatSession.getId());
        }

        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false).irisTutorSuggestionSettings();
        if (!settings.enabled()) {
            throw new ConflictException("Tutor Suggestions are not enabled for this course", "Iris", "irisDisabled");
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
                        var user = userRepository.findByIdElseThrow(session.getUserId());
                        var latestSubmission = getLatestSubmissionIfExists(programmingExercise, user);
                        PyrisSubmissionDTO pyrisSubmissionDTO = latestSubmission.map(pyrisDTOService::toPyrisSubmissionDTO).orElse(null);
                        PyrisProgrammingExerciseDTO pyrisProgrammingExerciseDTO = pyrisDTOService.toPyrisProgrammingExerciseDTO(programmingExercise);
                        if (pyrisSubmissionDTO != null) {
                            submissionDTOOptional = Optional.of(pyrisSubmissionDTO);
                        }
                        programmingExerciseDTOOptional = Optional.of(pyrisProgrammingExerciseDTO);
                    }
                    case TEXT -> {
                        TextExercise textExercise = (TextExercise) exercise;
                        textExerciseDTOOptional = Optional.of(PyrisTextExerciseDTO.of(textExercise));
                    }
                }
            }
            pyrisPipelineService.executeTutorSuggestionPipeline(variant, chatSession, event, lectureIdOptional, textExerciseDTOOptional, submissionDTOOptional,
                    programmingExerciseDTOOptional, postDTO);
        }
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }

    @Override
    public void checkHasAccessTo(User user, IrisTutorSuggestionSession irisSession) {
        var post = postRepository.findPostOrMessagePostByIdElseThrow(irisSession.getPostId());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, post.getCoursePostingBelongsTo(), user);
        if (irisSession.getUserId() != user.getId()) {
            throw new AccessForbiddenException("Iris Session", irisSession.getId());
        }
    }

    @Override
    public void checkIsFeatureActivatedFor(IrisTutorSuggestionSession irisSession) {
        var post = postRepository.findPostOrMessagePostByIdElseThrow(irisSession.getPostId());
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.TUTOR_SUGGESTION, post.getCoursePostingBelongsTo());
    }

    /**
     * Handles tutor suggestion job status updates.
     *
     * @param job          The job to handle
     * @param statusUpdate The status update to handle
     * @return The updated job
     */
    public TrackedSessionBasedPyrisJob handleStatusUpdate(TrackedSessionBasedPyrisJob job, TutorSuggestionStatusUpdateDTO statusUpdate) {
        var session = (IrisTutorSuggestionSession) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
        IrisMessage savedMessage;
        IrisMessage savedArtifact;
        if (statusUpdate.artifact() != null || statusUpdate.result() != null) {
            if (statusUpdate.artifact() != null) {
                var artifact = new IrisMessage();
                artifact.addContent(new IrisTextMessageContent(statusUpdate.artifact()));
                savedArtifact = irisMessageService.saveMessage(artifact, session, IrisMessageSender.ARTIFACT);
                irisChatWebsocketService.sendMessage(session, savedArtifact, statusUpdate.stages());
            }
            else {
                savedArtifact = null;
            }
            if (statusUpdate.result() != null && !statusUpdate.result().isEmpty()) {
                var message = new IrisMessage();
                message.addContent(new IrisTextMessageContent(statusUpdate.result()));
                savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
                irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
            }
            else {
                savedMessage = null;
            }
        }
        else {
            savedMessage = null;
            savedArtifact = null;
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), null, statusUpdate.tokens());
        }

        AtomicReference<TrackedSessionBasedPyrisJob> updatedJob = new AtomicReference<>(job);
        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            if (savedMessage != null || savedArtifact != null) {
                var messageId = savedArtifact != null ? savedArtifact.getId() : savedMessage.getId();
                // generated message is first sent and generated trace is saved
                var llmTokenUsageTrace = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                    builder.withIrisMessageID(messageId).withUser(session.getUserId());
                    this.setLLMTokenUsageParameters(builder, session);
                    return builder;
                });

                updatedJob.set(job.withTraceId(llmTokenUsageTrace.getId()));
            }
            else {
                // interaction suggestion is sent and appended to the generated trace if it exists
                Optional.ofNullable(job.traceId()).flatMap(llmTokenUsageService::findLLMTokenUsageTraceById)
                        .ifPresentOrElse(trace -> llmTokenUsageService.appendRequestsToTrace(statusUpdate.tokens(), trace), () -> {
                            var llmTokenUsage = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                                builder.withUser(session.getUserId());
                                this.setLLMTokenUsageParameters(builder, session);
                                return builder;
                            });

                            updatedJob.set(job.withTraceId(llmTokenUsage.getId()));
                        });
            }
        }

        return updatedJob.get();
    }
}
