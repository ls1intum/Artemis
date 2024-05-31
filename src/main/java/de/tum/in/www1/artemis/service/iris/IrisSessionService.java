package de.tum.in.www1.artemis.service.iris;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisCompetencyGenerationSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.iris.session.IrisChatBasedFeatureInterface;
import de.tum.in.www1.artemis.service.iris.session.IrisCompetencyGenerationSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisHestiaSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisRateLimitedFeatureInterface;
import de.tum.in.www1.artemis.service.iris.session.IrisSubFeatureInterface;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service for managing Iris sessions.
 */
@Service
@Profile("iris")
public class IrisSessionService {

    private final UserRepository userRepository;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    private final IrisHestiaSessionService irisHestiaSessionService;

    private final IrisCompetencyGenerationSessionService irisCompetencyGenerationSessionService;

    public IrisSessionService(UserRepository userRepository, IrisExerciseChatSessionService irisExerciseChatSessionService,
            IrisCourseChatSessionService irisCourseChatSessionService, IrisHestiaSessionService irisHestiaSessionService,
            IrisCompetencyGenerationSessionService irisCompetencyGenerationSessionService) {
        this.userRepository = userRepository;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.irisCourseChatSessionService = irisCourseChatSessionService;
        this.irisHestiaSessionService = irisHestiaSessionService;
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
     * @throws AccessForbiddenException If the user has not accepted the Iris privacy policy yet
     */
    public void checkHasAccessToIrisSession(IrisSession session, @Nullable User user) {
        if (user == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        user.hasAcceptedIrisElseThrow();
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
        return switch (session) {
            case IrisExerciseChatSession chatSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisExerciseChatSessionService, chatSession);
            case IrisCourseChatSession courseChatSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisCourseChatSessionService, courseChatSession);
            case IrisHestiaSession hestiaSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisHestiaSessionService, hestiaSession);
            case IrisCompetencyGenerationSession irisCompetencyGenerationSession ->
                (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisCompetencyGenerationSessionService, irisCompetencyGenerationSession);
            case null, default -> throw new BadRequestException("Unknown Iris session type " + session.getClass().getSimpleName());
        };
    }

    private record IrisSubFeatureWrapper<S extends IrisSession>(IrisSubFeatureInterface<S> irisSubFeatureInterface, S irisSession) {
    }
}
