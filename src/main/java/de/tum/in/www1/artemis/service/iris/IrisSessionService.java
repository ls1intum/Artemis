package de.tum.in.www1.artemis.service.iris;

import javax.ws.rs.BadRequestException;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisHestiaSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisSessionSubServiceInterface;

/**
 * Service for managing Iris sessions.
 */
@Service
public class IrisSessionService {

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisHestiaSessionService irisHestiaSessionService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    public IrisSessionService(UserRepository userRepository, IrisChatSessionService irisChatSessionService, IrisHestiaSessionService irisHestiaSessionService,
            IrisChatSessionRepository irisChatSessionRepository) {
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisHestiaSessionService = irisHestiaSessionService;
        this.irisChatSessionRepository = irisChatSessionRepository;
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     * If a session already exists, a BadRequestException is thrown.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    public IrisSession createChatSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (irisChatSessionRepository.findByExerciseIdAndUserId(exercise.getId(), user.getId()).isPresent()) {
            throw new BadRequestException("Iris Session already exists for exercise " + exercise.getId() + " and user " + user.getId());
        }

        var irisSession = new IrisChatSession();
        irisSession.setExercise(exercise);
        irisSession.setUser(user);
        return irisChatSessionRepository.save(irisSession);
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

    private IrisSessionSubServiceInterface getIrisSessionSubService(IrisSession session) {
        if (session instanceof IrisChatSession) {
            return irisChatSessionService;
        }
        else if (session instanceof IrisHestiaSession) {
            return irisHestiaSessionService;
        }
        else {
            throw new BadRequestException("Unknown Iris session type " + session.getClass().getSimpleName());
        }
    }
}
