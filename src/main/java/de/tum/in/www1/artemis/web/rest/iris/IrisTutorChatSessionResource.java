package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisTutorChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisTutorChatSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisTutorChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/tutor-chat/")
public class IrisTutorChatSessionResource {

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final ExerciseRepository exerciseRepository;

    private final IrisTutorChatSessionRepository irisTutorChatSessionRepository;

    protected IrisTutorChatSessionResource(AuthorizationCheckService authCheckService, IrisTutorChatSessionRepository irisTutorChatSessionRepository, UserRepository userRepository,
            ExerciseRepository exerciseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator,
            IrisRateLimitService irisRateLimitService) {
        this.irisTutorChatSessionRepository = irisTutorChatSessionRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET tutor-chat/{exerciseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{exerciseId}/sessions/current")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisTutorChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var sessionOptional = irisTutorChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId());
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            irisSessionService.checkHasAccessToIrisSession(session, user);
            return ResponseEntity.ok(session);
        }

        return createSessionForExercise(exerciseId);
    }

    /**
     * GET tutor-chat/{exerciseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IrisTutorChatSession>> getAllSessions(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var sessions = irisTutorChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST tutor-chat/{exerciseId}/session: Create a new iris session for an exercise and user.
     * If there already exists an iris session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisTutorChatSession> createSessionForExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var session = irisTutorChatSessionRepository.save(new IrisTutorChatSession(programmingExercise, user));
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    private static ProgrammingExercise validateExercise(Exercise exercise) {
        if (!(exercise instanceof ProgrammingExercise programmingExercise)) {
            throw new ConflictException("Iris is only supported for programming exercises", "Iris", "irisProgrammingExercise");
        }
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return programmingExercise;
    }
}
