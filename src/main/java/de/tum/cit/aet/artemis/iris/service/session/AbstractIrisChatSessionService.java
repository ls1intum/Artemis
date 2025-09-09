package de.tum.cit.aet.artemis.iris.service.session;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface {

    private final IrisSessionRepository irisSessionRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final IrisMessageService irisMessageService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ObjectMapper objectMapper;

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService) {
        this.irisSessionRepository = irisSessionRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.objectMapper = objectMapper;
        this.irisMessageService = irisMessageService;
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
     * Handles the status update of a ExerciseChatJob by sending the result to the student via the Websocket.
     *
     * @param job          The job that was executed
     * @param statusUpdate The status update of the job
     * @return the same job record or a new job record with the same job id if changes were made
     */
    public TrackedSessionBasedPyrisJob handleStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var session = (S) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
        IrisMessage savedMessage;
        boolean statusSent = false;
        if (statusUpdate.sessionTitle() != null && !statusUpdate.sessionTitle().isBlank()) {
            session.setTitle(statusUpdate.sessionTitle());
            irisSessionRepository.save(session);
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), statusUpdate.sessionTitle(), statusUpdate.suggestions(), statusUpdate.tokens());
            statusSent = true;
        }
        if (statusUpdate.result() != null) {
            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
        }
        else {
            savedMessage = null;
            if (!statusSent) {
                irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), statusUpdate.sessionTitle(), statusUpdate.suggestions(), statusUpdate.tokens());
            }
        }

        AtomicReference<TrackedSessionBasedPyrisJob> updatedJob = new AtomicReference<>(job);
        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            if (savedMessage != null) {
                // generated message is first sent and generated trace is saved
                var llmTokenUsageTrace = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                    builder.withIrisMessageID(savedMessage.getId()).withUser(session.getUserId());
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
