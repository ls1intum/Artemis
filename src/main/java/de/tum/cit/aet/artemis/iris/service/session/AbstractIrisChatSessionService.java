package de.tum.cit.aet.artemis.iris.service.session;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.MessageSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface<S> {

    private static final int MAX_SESSION_TITLE_LENGTH = 255;

    private final IrisSessionRepository irisSessionRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ObjectMapper objectMapper;

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService) {
        this.irisSessionRepository = irisSessionRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.objectMapper = objectMapper;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.llmTokenUsageService = llmTokenUsageService;
    }

    /**
     * Updates the latest suggestions of the session.
     * Converts the list of latest suggestions to a JSON string.
     * The updated suggestions are then saved to the session in the database.
     *
     * @param session           The session to update
     * @param latestSuggestions The latest suggestions to set
     */
    protected void updateLatestSuggestions(S session, List<String> latestSuggestions) {
        if (latestSuggestions == null || latestSuggestions.isEmpty()) {
            return;
        }
        try {
            var suggestions = objectMapper.writeValueAsString(latestSuggestions);
            session.setLatestSuggestions(suggestions);
            irisSessionRepository.save(session);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Could not update latest suggestions for session " + session.getId(), e);
        }
    }

    /**
     * Sets and saves the session title if provided.
     * Truncates the title to 255 characters if necessary.
     * This is a utility method that can be used by services that don't extend this class.
     *
     * @param <T>               The type of chat session (must extend IrisChatSession)
     * @param session           The session to update
     * @param sessionTitle      The title to set (can be null or blank)
     * @param sessionRepository The repository to save the session
     * @return The truncated session title if it was set, null otherwise
     */
    public static <T extends IrisChatSession> String setSessionTitle(T session, String sessionTitle, IrisSessionRepository sessionRepository) {
        if (sessionTitle != null && !sessionTitle.isBlank()) {
            String truncatedTitle = sessionTitle.length() > MAX_SESSION_TITLE_LENGTH ? sessionTitle.substring(0, MAX_SESSION_TITLE_LENGTH) : sessionTitle;
            session.setTitle(truncatedTitle);
            sessionRepository.save(session);
            return truncatedTitle;
        }
        return null;
    }

    /**
     * Return a localized "New chat" title based on the user's language key.
     *
     * @param langKey       The language key of the user
     * @param messageSource The message source to resolve titles from
     * @return the localized title
     */
    public static String getLocalizedNewChatTitle(String langKey, MessageSource messageSource) {
        Locale locale = langKey == null || langKey.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(langKey);
        return messageSource.getMessage("iris.chat.session.newChatTitle", null, "New Chat", locale);
    }

    /**
     * Handles the status update of an ExerciseChatJob by sending the result to the student via the Websocket.
     *
     * @param job          The job that was executed
     * @param statusUpdate The status update of the job
     * @return the same job record or a new job record with the same job id if changes were made
     */
    public TrackedSessionBasedPyrisJob handleStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        // noinspection unchecked
        var session = (S) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
        AtomicReference<TrackedSessionBasedPyrisJob> updatedJob = new AtomicReference<>(job);
        IrisMessage savedMessage;

        String sessionTitle = AbstractIrisChatSessionService.setSessionTitle(session, statusUpdate.sessionTitle(), irisSessionRepository);
        if (statusUpdate.result() != null) {
            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            message.setAccessedMemories(statusUpdate.accessedMemories());
            message.setCreatedMemories(statusUpdate.createdMemories());
            savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            updatedJob.getAndUpdate(j -> j.withAssistantMessageId(savedMessage.getId()));
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages(), sessionTitle);
        }
        else {
            savedMessage = null;
            if (statusUpdate.accessedMemories() != null && !statusUpdate.accessedMemories().isEmpty() && job.userMessageId() != null) {
                irisMessageRepository.findById(job.userMessageId()).ifPresent(message -> {
                    message.setAccessedMemories(statusUpdate.accessedMemories());
                    irisMessageRepository.save(message);
                    irisChatWebsocketService.sendMessage(session, message, statusUpdate.stages());
                });
            }
            if (statusUpdate.createdMemories() != null && !statusUpdate.createdMemories().isEmpty() && job.assistantMessageId() != null) {
                irisMessageRepository.findById(job.assistantMessageId()).ifPresent(message -> {
                    message.setCreatedMemories(statusUpdate.createdMemories());
                    irisMessageRepository.save(message);
                    irisChatWebsocketService.sendMessage(session, message, statusUpdate.stages());
                });
            }
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), sessionTitle, statusUpdate.suggestions(), statusUpdate.tokens());
        }

        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            if (savedMessage != null) {
                // generated message is first sent and generated trace is saved
                var llmTokenUsageTrace = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                    builder.withIrisMessageID(savedMessage.getId()).withUser(session.getUserId());
                    this.setLLMTokenUsageParameters(builder, session);
                    return builder;
                });

                updatedJob.getAndUpdate(j -> j.withTraceId(llmTokenUsageTrace.getId()));
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

                            updatedJob.getAndUpdate(j -> j.withTraceId(llmTokenUsage.getId()));
                        });
            }
        }

        updateLatestSuggestions(session, statusUpdate.suggestions());

        return updatedJob.get();
    }

    Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
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

    protected abstract void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, S session);
}
