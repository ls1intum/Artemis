package de.tum.in.www1.artemis.service.iris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
public class IrisChatService {

    private final Logger log = LoggerFactory.getLogger(IrisChatService.class);

    private final IrisModelService irisModelService;

    private final IrisMessageService irisMessageService;

    private final IrisWebsocketService irisWebsocketService;

    private final IrisSessionRepository irisSessionRepository;

    public IrisChatService(IrisModelService irisModelService, IrisMessageService irisMessageService, IrisWebsocketService irisWebsocketService,
            IrisSessionRepository irisSessionRepository) {
        this.irisModelService = irisModelService;
        this.irisMessageService = irisMessageService;
        this.irisWebsocketService = irisWebsocketService;
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    public void requestAndHandleResponse(IrisSession session) {
        var fullSession = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        irisModelService.requestResponse(fullSession).handleAsync((irisMessageOptional, throwable) -> {
            if (throwable != null) {
                log.error("Error while getting response from Iris model", throwable);
            }
            else if (irisMessageOptional.isPresent()) {
                var irisMessage = irisMessageService.saveMessage(irisMessageOptional.get(), fullSession, IrisMessageSender.LLM);
                irisWebsocketService.sendMessage(irisMessage);
            }
            else {
                log.error("No response from Iris model");
            }
            return null;
        });
    }

    /**
     * Requests the initial system message for the used LLM
     *
     * @return The initial system message for the used LLM, if no LLM is set, returns null
     */
    public String requestInitialSystemMessage() {
        return irisModelService.requestInitialSystemMessage().orElse(null);
    }
}
