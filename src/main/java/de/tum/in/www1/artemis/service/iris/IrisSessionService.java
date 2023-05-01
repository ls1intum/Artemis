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

@Service
public class IrisSessionService {

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final IrisSessionRepository irisSessionRepository;

    public IrisSessionService(AuthorizationCheckService authCheckService, UserRepository userRepository, IrisSessionRepository irisSessionRepository) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.irisSessionRepository = irisSessionRepository;
    }

    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        if (user == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, session.getExercise(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    public IrisSession createSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (irisSessionRepository.findByExerciseIdAndUserId(exercise.getId(), user.getId()).isPresent()) {
            throw new BadRequestException("Iris Session already exists for exercise " + exercise.getId() + " and user " + user.getId());
        }

        var irisSession = new IrisSession();
        irisSession.setExercise(exercise);
        irisSession.setUser(user);
        return irisSessionRepository.save(irisSession);
    }
}
