package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.BadRequestException;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatBasedFeatureInterface;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisRateLimitedFeatureInterface;
import de.tum.cit.aet.artemis.iris.service.session.IrisSubFeatureInterface;
import de.tum.cit.aet.artemis.iris.service.session.IrisTextExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Service for managing Iris sessions.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisSessionService {

    private final UserRepository userRepository;

    private final IrisTextExerciseChatSessionService irisTextExerciseChatSessionService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisTutorSuggestionSessionService irisTutorSuggestionSessionService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final IrisSettingsService irisSettingsService;

    public IrisSessionService(UserRepository userRepository, IrisTextExerciseChatSessionService irisTextExerciseChatSessionService,
            IrisExerciseChatSessionService irisExerciseChatSessionService, IrisCourseChatSessionService irisCourseChatSessionService,
            IrisLectureChatSessionService irisLectureChatSessionService, IrisTutorSuggestionSessionService irisTutorSuggestionSessionService,
            IrisChatSessionRepository irisChatSessionRepository, IrisSettingsService irisSettingsService) {
        this.userRepository = userRepository;
        this.irisTextExerciseChatSessionService = irisTextExerciseChatSessionService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.irisCourseChatSessionService = irisCourseChatSessionService;
        this.irisLectureChatSessionService = irisLectureChatSessionService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.irisSettingsService = irisSettingsService;
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
        var wrapper = getIrisSessionSubService(session);
        if (session.shouldAcceptExternalLLMUsage()) {
            user.hasAcceptedExternalLLMUsageElseThrow();
        }
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
            chatWrapper.sendOverWebsocket(session, message);
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
            case IrisTextExerciseChatSession chatSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisTextExerciseChatSessionService, chatSession);
            case IrisProgrammingExerciseChatSession chatSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisExerciseChatSessionService, chatSession);
            case IrisCourseChatSession courseChatSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisCourseChatSessionService, courseChatSession);
            case IrisLectureChatSession lectureChatSession -> (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisLectureChatSessionService, lectureChatSession);
            case IrisTutorSuggestionSession tutorSuggestionSession ->
                (IrisSubFeatureWrapper<S>) new IrisSubFeatureWrapper<>(irisTutorSuggestionSessionService, tutorSuggestionSession);
            case null, default -> throw new BadRequestException("Unknown Iris session type " + session.getClass().getSimpleName());
        };
    }

    private record IrisSubFeatureWrapper<S extends IrisSession>(IrisSubFeatureInterface<S> irisSubFeatureInterface, S irisSession) {
    }

    /**
     * Get all IrisChatSessions for a course and map the IrisChatSessionDAO to IrisChatSessionDTO
     *
     * @param course The course
     * @param userId The id of the user
     * @return A list of all IrisChatSessionsDTOs for a course
     */
    public List<IrisChatSessionDTO> getIrisSessionsByCourseAndUserId(Course course, Long userId) {
        var settings = irisSettingsService.getCombinedIrisSettingsForCourse(course.getId(), true);
        List<Class<? extends IrisChatSession>> enabledTypes = new ArrayList<>();

        if (settings.irisTextExerciseChatSettings().enabled()) {
            enabledTypes.add(IrisTextExerciseChatSession.class);
        }

        if (settings.irisProgrammingExerciseChatSettings().enabled()) {
            enabledTypes.add(IrisProgrammingExerciseChatSession.class);
        }

        if (settings.irisCourseChatSettings().enabled()) {
            enabledTypes.add(IrisCourseChatSession.class);
        }

        if (settings.irisLectureChatSettings().enabled()) {
            enabledTypes.add(IrisLectureChatSession.class);
        }

        return irisChatSessionRepository.findByCourseIdAndUserId(course.getId(), userId, enabledTypes).stream().map(dao -> new IrisChatSessionDTO(dao.session().getId(),
                dao.entityId(), dao.entityName(), dao.session().getTitle(), dao.session().getCreationDate(), dao.session().getMode())).toList();
    }
}
