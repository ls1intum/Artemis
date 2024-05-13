package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/")
public class IrisChatSessionResource extends IrisExerciseChatBasedSessionResource<ProgrammingExercise, IrisChatSession> {

    private final IrisChatSessionRepository irisChatSessionRepository;

    protected IrisChatSessionResource(AuthorizationCheckService authCheckService, IrisChatSessionRepository irisChatSessionRepository, UserRepository userRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService) {
        super(authCheckService, userRepository, irisSessionService, irisSettingsService, pyrisHealthIndicator, irisRateLimitService, programmingExerciseRepository);
        this.irisChatSessionRepository = irisChatSessionRepository;
    }

    /**
     * GET programming-exercises/{exerciseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/sessions/current")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisChatSession> getCurrentSession(@PathVariable Long exerciseId) {
        return super.getCurrentSession(exerciseId, IrisSubSettingsType.CHAT, Role.STUDENT,
                (exercise, user) -> irisChatSessionRepository.findNewestByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId()));
    }

    /**
     * GET programming-exercises/{exerciseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IrisChatSession>> getAllSessions(@PathVariable Long exerciseId) {
        return super.getAllSessions(exerciseId, IrisSubSettingsType.CHAT, Role.STUDENT,
                (exercise, user) -> irisChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId()));
    }

    /**
     * POST programming-exercises/{exerciseId}/session: Create a new iris session for an exercise and user.
     * If there already exists an iris session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisChatSession> createSessionForProgrammingExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        return super.createSessionForExercise(exerciseId, IrisSubSettingsType.CHAT, Role.STUDENT, (exercise, user) -> {
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                return irisChatSessionRepository.save(new IrisChatSession(programmingExercise, user));
            }
            else {
                throw new ConflictException("Iris is only supported for programming exercises", "Iris", "irisProgrammingExercise");
            }
        });
    }

    /**
     * GET iris/sessions/{sessionId}/active: Retrieve if Iris is active for a session
     * This checks if the used model is healthy.
     *
     * @param sessionId id of the session
     * @return a status {@code 200 (Ok)} and with body true if Iris is active, false otherwise
     */
    @GetMapping("sessions/{sessionId}/active")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisHealthDTO> isIrisActive(@PathVariable Long sessionId) {
        var session = irisChatSessionRepository.findByIdElseThrow(sessionId);
        return ResponseEntity.ok(super.isIrisActiveInternal(session.getExercise(), session, IrisCombinedSettingsDTO::irisChatSettings));
    }
}
