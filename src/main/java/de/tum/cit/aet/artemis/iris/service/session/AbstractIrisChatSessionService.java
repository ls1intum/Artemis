package de.tum.cit.aet.artemis.iris.service.session;

import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.SessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

public abstract class AbstractIrisChatSessionService<S extends IrisChatSession> implements IrisChatBasedFeatureInterface<S>, IrisRateLimitedFeatureInterface {

    private final IrisSessionRepository irisSessionRepository;

    private final IrisMessageService irisMessageService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ObjectMapper objectMapper;

    protected final HashMap<String, LLMTokenUsageTrace> traces = new HashMap<>();

    public AbstractIrisChatSessionService(IrisSessionRepository irisSessionRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService) {
        this.irisSessionRepository = irisSessionRepository;
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
     */
    public void handleStatusUpdate(SessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var session = (S) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
        IrisMessage savedMessage;
        if (statusUpdate.result() != null) {
            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
        }
        else {
            savedMessage = null;
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), statusUpdate.suggestions(), statusUpdate.tokens());
        }

        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            if (savedMessage != null) {
                // generated message is first sent and generated trace is saved
                var llmTokenUsageTrace = llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                    builder.withIrisMessageID(savedMessage.getId()).withUser(session.getUser().getId());
                    this.setLLMTokenUsageParameters(builder, session);
                    return builder;
                });
                traces.put(job.jobId(), llmTokenUsageTrace);
            }
            else {
                // interaction suggestion is sent and appended to the generated trace if it exists, trace is then removed,
                // because interaction suggestion is the last message from Iris in the pipeline
                if (traces.containsKey(job.jobId())) {
                    var trace = traces.get(job.jobId());
                    llmTokenUsageService.appendRequestsToTrace(statusUpdate.tokens(), trace);
                    traces.remove(job.jobId());
                }
                else {
                    llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> {
                        builder.withUser(session.getUser().getId());
                        this.setLLMTokenUsageParameters(builder, session);
                        return builder;
                    });
                }
            }
        }

        updateLatestSuggestions(session, statusUpdate.suggestions());
    }

    protected abstract void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, S session);
}
