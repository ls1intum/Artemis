package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisHealthStatusDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSubSettingsInterface;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller base class for managing exercise chat based {@link IrisSession}s.
 */
public abstract class IrisExerciseChatBasedSessionResource<E extends Exercise, S extends IrisSession> {

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final ProgrammingExerciseRepository programmingExerciseRepository;

    protected IrisExerciseChatBasedSessionResource(AuthorizationCheckService authCheckService, UserRepository userRepository, IrisSessionService irisSessionService,
            IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    protected ResponseEntity<S> getCurrentSession(Long exerciseId, IrisSubSettingsType subSettingsType, Role role, BiFunction<Exercise, User, S> sessionsFunction) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.isEnabledForElseThrow(subSettingsType, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(role, exercise, user);

        var session = sessionsFunction.apply(exercise, user);
        irisSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    protected ResponseEntity<List<S>> getAllSessions(Long exerciseId, IrisSubSettingsType subSettingsType, Role role, BiFunction<Exercise, User, List<S>> sessionsFunction) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.isEnabledForElseThrow(subSettingsType, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(role, exercise, user);

        var sessions = sessionsFunction.apply(exercise, user);
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    protected ResponseEntity<S> createSessionForExercise(Long exerciseId, IrisSubSettingsType subSettingsType, Role role, BiFunction<Exercise, User, S> sessionsFunction)
            throws URISyntaxException {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        irisSettingsService.isEnabledForElseThrow(subSettingsType, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(role, exercise, user);

        var session = sessionsFunction.apply(exercise, user);

        var uriString = "/api/iris/sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    protected IrisHealthDTO isIrisActiveInternal(E exercise, S session, Function<IrisCombinedSettingsDTO, IrisCombinedSubSettingsInterface> subSettingsFunction) {
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkIsIrisActivated(session);
        var settings = irisSettingsService.getCombinedIrisSettingsFor(exercise, false);
        var health = pyrisHealthIndicator.health();
        PyrisHealthStatusDTO[] modelStatuses = (PyrisHealthStatusDTO[]) health.getDetails().get("modelStatuses");
        var specificModelStatus = false;
        if (modelStatuses != null) {
            specificModelStatus = Arrays.stream(modelStatuses).filter(x -> x.model().equals(subSettingsFunction.apply(settings).preferredModel()))
                    .anyMatch(x -> x.status() == PyrisHealthStatusDTO.ModelStatus.UP);
        }

        IrisRateLimitService.IrisRateLimitInformation rateLimitInfo = null;

        // TODO: Refactor rate limit service in the future
        if (session instanceof IrisChatSession) {
            rateLimitInfo = irisRateLimitService.getRateLimitInformation(user);
        }

        return new IrisHealthDTO(health.getStatus() == Status.UP, rateLimitInfo);
    }

    public record IrisHealthDTO(boolean active, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo) {
    }
}
