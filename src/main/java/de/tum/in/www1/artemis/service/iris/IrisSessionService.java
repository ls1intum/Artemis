package de.tum.in.www1.artemis.service.iris;

import java.util.Objects;

import javax.ws.rs.BadRequestException;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service for managing Iris sessions.
 */
@Service
public class IrisSessionService {

    private final UserRepository userRepository;

    private final IrisChatService irisChatService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    public IrisSessionService(UserRepository userRepository, IrisChatService irisChatService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository) {
        this.userRepository = userRepository;
        this.irisChatService = irisChatService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param session The session to check
     * @param user    The user to check
     */
    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        if (user == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, session.getExercise(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the programming exercise for which an Iris operation was requested has Iris activated.
     * An Iris operation can be performed if the programming exercise has Iris activated.
     *
     * @param programmingExercise The programming exercise to check
     */
    public void checkIrisActivated(ProgrammingExercise programmingExercise) {
        if (!programmingExercise.isIrisActivated()) {
            throw new BadRequestException("Iris not activated for Programming Exercise: " + programmingExercise.getId());
        }
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     * If a session already exists, a BadRequestException is thrown.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    public IrisSession createSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (irisSessionRepository.findByExerciseIdAndUserId(exercise.getId(), user.getId()).isPresent()) {
            throw new BadRequestException("Iris Session already exists for exercise " + exercise.getId() + " and user " + user.getId());
        }

        var irisSession = new IrisSession();
        irisSession.setExercise(exercise);
        irisSession.setUser(user);
        return irisSessionRepository.save(irisSession);
    }

    /**
     * Sends a request to Iris to get a message for the given session.
     * It decides which Iris subsystem should handle it based on the session type.
     * Currently, only the chat subsystem exists.
     *
     * @param session The session to get a message for
     */
    public void requestMessageFromIris(IrisSession session) {
        // TODO: Future: Switch between different session types
        irisChatService.requestAndHandleResponse(session);
    }
}
