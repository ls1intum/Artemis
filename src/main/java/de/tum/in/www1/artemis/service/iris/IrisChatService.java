package de.tum.in.www1.artemis.service.iris;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

    private final IrisSessionRepository irisSessionRepository;

    public IrisChatService(IrisModelService irisModelService, IrisMessageService irisMessageService, IrisSessionRepository irisSessionRepository) {
        this.irisModelService = irisModelService;
        this.irisMessageService = irisMessageService;
        this.irisSessionRepository = irisSessionRepository;
    }

    @Async
    public void sendToLLM(IrisSession session) {
        var fullSession = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        try {
            var irisMessageOptional = irisModelService.getResponse(fullSession).get();
            if (irisMessageOptional.isPresent()) {
                var irisMessage = irisMessageService.saveMessage(irisMessageOptional.get(), fullSession, IrisMessageSender.LLM);
                // TODO: Send over websocket
            }
            else {
                log.error("No response from Iris model");
            }
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Error while getting response from Iris model", e);
        }
    }
}
