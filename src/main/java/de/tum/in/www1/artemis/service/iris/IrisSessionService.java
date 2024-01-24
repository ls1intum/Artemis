package de.tum.in.www1.artemis.service.iris;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.iris.session.*;

/**
 * Service for managing Iris sessions.
 */
@Service
@Profile("iris")
public class IrisSessionService {

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisHestiaSessionService irisHestiaSessionService;

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisCompetencyGenerationSessionService irisCompetencyGenerationSessionService;

    public IrisSessionService(UserRepository userRepository, IrisChatSessionService irisChatSessionService, IrisHestiaSessionService irisHestiaSessionService,
            IrisCodeEditorSessionService irisCodeEditorSessionService, IrisCompetencyGenerationSessionService irisCompetencyGenerationSessionService) {
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisHestiaSessionService = irisHestiaSessionService;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisCompetencyGenerationSessionService = irisCompetencyGenerationSessionService;
    }

    /**
     * Checks if the exercise connected to the session has Iris activated
     *
     * @param session the session to check for
     */
    public void checkIsIrisActivated(IrisSession session) {
        var wrapper = getIrisSessionSubService(session);
        wrapper.irisSubFeatureInterface.checkIsFeatureActivatedFor(wrapper.irisSession);
    }

    /**
     * Checks if the user has access to the Iris session.
     * If the user is null, the user is fetched from the database.
     *
     * @param session The session to check
     * @param user    The user to check
     */
    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        if (user == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        var wrapper = getIrisSessionSubService(session);
        wrapper.irisSubFeatureInterface.checkHasAccessTo(user, wrapper.irisSession);
    }

    /**
     * Sends a request to Iris to get a message for the given session.
     * It decides which Iris subsystem should handle it based on the session type.
     *
     * @param session The session to get a message for
     * @param <S>     The type of the session
     * @throws BadRequestException If the session type is invalid
     */
    public <S extends IrisSession> void requestMessageFromIris(S session) {
        var wrapper = getIrisSessionSubService(session);
        if (wrapper.irisSubFeatureInterface instanceof IrisChatBasedFeatureInterface<S> chatWrapper) {
            chatWrapper.requestAndHandleResponse(wrapper.irisSession);
        }
        else {
            throw new BadRequestException("Invalid Iris session type " + session.getClass().getSimpleName());
        }
    }

    /**
     * Sends a message over the websocket to a specific user.
     * It decides which Iris subsystem should handle it based on the session type.
     *
     * @param message The message to send
     * @param session The session to send the message for
     * @param <S>     The type of the session
     * @throws BadRequestException If the session type is invalid
     */
    public <S extends IrisSession> void sendOverWebsocket(IrisMessage message, S session) {
        var wrapper = getIrisSessionSubService(session);
        if (wrapper.irisSubFeatureInterface instanceof IrisChatBasedFeatureInterface<S> chatWrapper) {
            chatWrapper.sendOverWebsocket(message);
        }
        else {
            throw new BadRequestException("Invalid Iris session type " + message.getSession().getClass().getSimpleName());
        }
    }

    /**
     * Checks the rate limit for the given user.
     * It decides which Iris subsystem should handle it based on the session type.
     *
     * @param session The session to check the rate limit for
     * @param user    The user to check the rate limit for
     */
    public void checkRateLimit(IrisSession session, User user) {
        var wrapper = getIrisSessionSubService(session);
        if (wrapper.irisSubFeatureInterface instanceof IrisRateLimitedFeatureInterface rateLimitedWrapper) {
            rateLimitedWrapper.checkRateLimit(user);
        }
    }

    /**
     * Gets the Iris subsystem for the given session.
     * Uses generic casts that are safe because the Iris subsystems are only used for the correct session type.
     *
     * @param session The session to get the subsystem for
     * @param <S>     The type of the session
     * @throws BadRequestException If the session type is unknown
     * @return The Iris subsystem for the session
     */
    @SuppressWarnings("unchecked")
    private <S extends IrisSession> IrisSubFeatureWrapper<S> getIrisSessionSubService(S session) {
        if (session instanceof IrisChatSession chatSession) {
            return (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisChatSessionService, chatSession);
        }
        if (session instanceof IrisHestiaSession hestiaSession) {
            return (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisHestiaSessionService, hestiaSession);
        }
        if (session instanceof IrisCodeEditorSession codeEditorSession) {
            return (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisCodeEditorSessionService, codeEditorSession);
        }
        if (session instanceof IrisCompetencyGenerationSession irisCompetencyGenerationSession) {
            return (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisCompetencyGenerationSessionService, irisCompetencyGenerationSession);
        }
        throw new BadRequestException("Unknown Iris session type " + session.getClass().getSimpleName());
    }

    private record IrisSubFeatureWrapper<S extends IrisSession>(IrisSubFeatureInterface<S> irisSubFeatureInterface, S irisSession) {
    }
}
