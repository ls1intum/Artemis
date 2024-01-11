package de.tum.in.www1.artemis.service.iris;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.service.iris.session.*;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

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

    private final IrisChatSessionRepository irisChatSessionRepository;

    public IrisSessionService(UserRepository userRepository, IrisChatSessionService irisChatSessionService, IrisHestiaSessionService irisHestiaSessionService,
            IrisCodeEditorSessionService irisCodeEditorSessionService, IrisCompetencyGenerationSessionService irisCompetencyGenerationSessionService,
            IrisChatSessionRepository irisChatSessionRepository) {
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisHestiaSessionService = irisHestiaSessionService;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisCompetencyGenerationSessionService = irisCompetencyGenerationSessionService;
        this.irisChatSessionRepository = irisChatSessionRepository;
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    public IrisChatSession createChatSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return irisChatSessionRepository.save(new IrisChatSession(exercise, user));
    }

    /**
     * Checks if the exercise connected to the session has Iris activated
     *
     * @param session the session to check for
     */
    public void checkIsIrisActivated(IrisSession session) {
        getIrisSessionSubService(session).checkIsIrisActivated(session);
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
        getIrisSessionSubService(session).checkHasAccessToIrisSession(session, user);
    }

    /**
     * Sends a request to Iris to get a message for the given session.
     * It decides which Iris subsystem should handle it based on the session type.
     * Currently, only the chat subsystem exists.
     *
     * @param session The session to get a message for
     */
    public void requestMessageFromIris(IrisSession session) {
        getIrisSessionSubService(session).requestAndHandleResponse(session);
    }

    public void checkRateLimit(IrisSession session, User user) {
        getIrisSessionSubService(session).checkRateLimit(user);
    }

    public void sendOverWebsocket(IrisMessage message) {
        getIrisSessionSubService(message.getSession()).sendOverWebsocket(message);
    }

    private IrisSessionSubServiceInterface getIrisSessionSubService(IrisSession session) {
        if (session instanceof IrisChatSession) {
            return irisChatSessionService;
        }
        if (session instanceof IrisHestiaSession) {
            return irisHestiaSessionService;
        }
        if (session instanceof IrisCodeEditorSession) {
            return irisCodeEditorSessionService;
        }
        if (session instanceof IrisCompetencyGenerationSession) {
            return irisCodeEditorSessionService;
        }
        if (session instanceof IrisCompetencyGenerationSession) {
            return irisCompetencyGenerationSessionService;
        }
        throw new BadRequestException("Unknown Iris session type " + session.getClass().getSimpleName());
    }
}
