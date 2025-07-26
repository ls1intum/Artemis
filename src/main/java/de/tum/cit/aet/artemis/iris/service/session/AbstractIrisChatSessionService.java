package de.tum.cit.aet.artemis.iris.service.session;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
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

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface {

    private final IrisSessionRepository irisSessionRepository;

    private final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ObjectMapper objectMapper;

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository, IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService) {
        this.irisSessionRepository = irisSessionRepository;
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
     * Handles the status update of a ExerciseChatJob by sending the result to the student via the Websocket.
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
        if (statusUpdate.result() != null) {
            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            message.setAccessedMemories(statusUpdate.accessedMemories());
            message.setCreatedMemories(statusUpdate.createdMemories());
            savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            updatedJob.getAndUpdate(j -> j.withAssistantMessageId(savedMessage.getId()));
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
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
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), statusUpdate.suggestions(), statusUpdate.tokens());
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

    protected abstract void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, S session);
}
