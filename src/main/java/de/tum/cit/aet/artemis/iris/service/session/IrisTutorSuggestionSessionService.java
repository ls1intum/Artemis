package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

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

    public IrisTutorSuggestionSessionService(IrisSessionRepository irisSessionRepository, ObjectMapper objectMapper, IrisMessageService irisMessageService,
            IrisChatWebsocketService irisChatWebsocketService, LLMTokenUsageService llmTokenUsageService, IrisRateLimitService rateLimitService,
            PyrisPipelineService pyrisPipelineService, AuthorizationCheckService authCheckService) {
        super(irisSessionRepository, objectMapper, irisMessageService, irisChatWebsocketService, llmTokenUsageService);
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.authCheckService = authCheckService;
    }

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisTutorSuggestionSession session) {
        // TODO: Implement
    }

    @Override
    public void sendOverWebsocket(IrisTutorSuggestionSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void requestAndHandleResponse(IrisTutorSuggestionSession irisSession) {
        requestAndHandleResponse(irisSession, Optional.empty());
    }

    public void requestAndHandleResponse(IrisTutorSuggestionSession session, Optional<String> event) {
        var chatSession = (IrisTutorSuggestionSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());

        var variant = "test";
        log.debug("IrisTutorSuggestionSessionService.requestAndHandleResponse");

        pyrisPipelineService.executeTutorSuggestionPipeline(variant, chatSession, event);
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
        // TODO: Implement after settings are implemented
    }
}
