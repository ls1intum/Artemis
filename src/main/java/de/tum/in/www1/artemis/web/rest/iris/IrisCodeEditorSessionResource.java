package de.tum.in.www1.artemis.web.rest.iris;

import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisHealthIndicator;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisStatusDTO;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * REST controller for managing {@link IrisCodeEditorSession}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisCodeEditorSessionResource {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisCodeEditorSessionRepository irisCodeEditorSessionRepository;

    private final UserRepository userRepository;

    private final IrisSessionService irisSessionService;

    private final IrisSettingsService irisSettingsService;

    private final IrisHealthIndicator irisHealthIndicator;

    public IrisCodeEditorSessionResource(ProgrammingExerciseRepository programmingExerciseRepository,
                                         AuthorizationCheckService authCheckService,
                                         IrisCodeEditorSessionRepository irisCodeEditorSessionRepository,
                                         UserRepository userRepository, IrisSessionService irisSessionService,
                                         IrisSettingsService irisSettingsService,
                                         IrisHealthIndicator irisHealthIndicator) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.irisHealthIndicator = irisHealthIndicator;
    }

    /**
     * GET programming-exercises/{exerciseId}/code-editor-sessions/current:
     * Retrieve the current iris code editor session for the programming exercise and logged-in user.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the
     * current iris code editor session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/code-editor-sessions/current")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSession> getCurrentSession(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
//        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var session = irisCodeEditorSessionRepository.findNewestByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        irisSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    /**
     * GET programming-exercises/{exerciseId}/code-editor-sessions:
     * Retrieve all Iris Sessions for the programming exercise and logged-in user
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with
     * body a list of the iris code editor sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/code-editor-sessions")
    @EnforceAtLeastEditor
    public ResponseEntity<List<IrisCodeEditorSession>> getAllSessions(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
//        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var sessions = irisCodeEditorSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST programming-exercises/{exerciseId}/code-editor-session:
     * Create a new iris code editor session for an exercise and user.
     * If there already exists such a session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/code-editor-session")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSession> createSession(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
//        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var session = irisSessionService.createCodeEditorSession(exercise, user);

        var uriString = "/api/iris/code-editor-sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * GET iris/sessions/{sessionId}/active:
     * Retrieve whether Iris is active for a session.
     * This checks if the used model is healthy.
     *
     * @param sessionId id of the session
     * @return a status {@code 200 (Ok)} and with body true if Iris is active, false otherwise
     */
    @GetMapping("/code-editor-sessions/{sessionId}/active")
    @EnforceAtLeastEditor
    public ResponseEntity<Boolean> isIrisActive(@PathVariable Long sessionId) {
        var session = irisCodeEditorSessionRepository.findByIdElseThrow(sessionId);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkIsIrisActivated(session);
        var settings = irisSettingsService.getCombinedIrisSettings(session.getExercise(), false);
        var health = irisHealthIndicator.health();
        IrisStatusDTO[] modelStatuses = (IrisStatusDTO[]) health.getDetails().get("modelStatuses");
        var specificModelStatus = false;
        if (modelStatuses != null) {
            specificModelStatus = Arrays.stream(modelStatuses).filter(x -> x.model().equals(settings.getIrisChatSettings().getPreferredModel()))
                    .anyMatch(x -> x.status() == IrisStatusDTO.ModelStatus.UP);
        }
        return ResponseEntity.ok(specificModelStatus);
    }
}
