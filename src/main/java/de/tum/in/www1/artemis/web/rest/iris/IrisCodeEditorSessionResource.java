package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisHealthIndicator;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisStatusDTO;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisCodeEditorSession}.
 * TODO: Lots of duplication with IrisChatSessionResource. Problem is lots of overlap but a few very important differences.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisCodeEditorSessionResource {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisCodeEditorSessionRepository irisCodeEditorSessionRepository;

    private final UserRepository userRepository;

    private final IrisCodeEditorSessionService irisCodeEditorSessionService;

    private final IrisSettingsService irisSettingsService;

    private final IrisHealthIndicator irisHealthIndicator;

    public IrisCodeEditorSessionResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository, UserRepository userRepository, IrisCodeEditorSessionService irisCodeEditorSessionService,
            IrisSettingsService irisSettingsService, IrisHealthIndicator irisHealthIndicator) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
        this.userRepository = userRepository;
        this.irisCodeEditorSessionService = irisCodeEditorSessionService;
        this.irisSettingsService = irisSettingsService;
        this.irisHealthIndicator = irisHealthIndicator;
    }

    /**
     * GET programming-exercises/{exerciseId}/code-editor-sessions/current: Retrieve the current iris code editor
     * session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris code editor
     *         session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/code-editor-sessions/current")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSession> getCurrentSession(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CODE_EDITOR, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var session = irisCodeEditorSessionRepository.findNewestByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        irisCodeEditorSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    /**
     * GET programming-exercises/{exerciseId}/code-editor-sessions: Retrieve all Iris Code Editor Sessions for the
     * programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris code editor
     *         sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/code-editor-sessions")
    @EnforceAtLeastEditor
    public ResponseEntity<List<IrisCodeEditorSession>> getAllSessions(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CODE_EDITOR, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var sessions = irisCodeEditorSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        sessions.forEach(s -> irisCodeEditorSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST programming-exercises/{exerciseId}/code-editor-sessions: Create a new iris code editor session for an
     * exercise and user. If there already exists an iris session for the exercise and user, a new one is created. Note:
     * The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris code editor session
     *         for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/code-editor-sessions")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSession> createSessionForProgrammingExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CODE_EDITOR, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var session = irisCodeEditorSessionService.createSession(exercise, user);

        var uriString = "/api/iris/code-editor-sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * POST programming-exercises/{exerciseId}/code-editor-sessions/autogenerate: Create a new iris code editor session
     * which automatically generates the repositories from the problem statement.
     * This is intended for use with newly-created exercises where the repositories have just been generated.
     * However, if there already exists an iris session for the exercise and user anyway, a new one is created.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris code editor session
     *         for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/code-editor-sessions/kickstart")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSession> generateRepositoriesFromProblemStatement(@PathVariable Long exerciseId) throws URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CODE_EDITOR, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        var session = irisCodeEditorSessionService.createSession(exercise, user);
        var kickstarterMessage = irisCodeEditorSessionService.createRepositoryGenerationPlan(session);
        irisCodeEditorSessionService.executePlan(session, kickstarterMessage.getContent().get(0).getId());

        var uriString = "/api/iris/code-editor-sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * GET iris/code-editor-sessions/{sessionId}/active: Retrieve if Iris is active for a session This checks if the
     * used model is healthy.
     *
     * @param sessionId id of the session
     * @return a status {@code 200 (Ok)} and with body true if Iris is active, false otherwise
     */
    @GetMapping("/code-editor-sessions/{sessionId}/active")
    @EnforceAtLeastEditor
    public ResponseEntity<Boolean> isIrisActive(@PathVariable Long sessionId) {
        var session = irisCodeEditorSessionRepository.findByIdElseThrow(sessionId);
        var user = userRepository.getUser();
        irisCodeEditorSessionService.checkHasAccessToIrisSession(session, user);
        irisCodeEditorSessionService.checkIsIrisActivated(session);
        var settings = irisSettingsService.getCombinedIrisSettingsFor(session.getExercise(), false);
        var health = irisHealthIndicator.health();
        IrisStatusDTO[] modelStatuses = (IrisStatusDTO[]) health.getDetails().get("modelStatuses");
        var specificModelStatus = false;
        if (modelStatuses != null) {
            specificModelStatus = Arrays.stream(modelStatuses).filter(x -> x.model().equals(settings.irisCodeEditorSettings().getPreferredModel()))
                    .anyMatch(x -> x.status() == IrisStatusDTO.ModelStatus.UP);
        }
        return ResponseEntity.ok(specificModelStatus);
    }
}
