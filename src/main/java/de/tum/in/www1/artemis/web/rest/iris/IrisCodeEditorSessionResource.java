package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisHealthIndicator;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisCodeEditorSession}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisCodeEditorSessionResource extends IrisExerciseChatBasedSessionResource<ProgrammingExercise, IrisCodeEditorSession> {

    private final IrisCodeEditorSessionRepository irisCodeEditorSessionRepository;

    protected IrisCodeEditorSessionResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, IrisHealthIndicator irisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisCodeEditorSessionRepository irisCodeEditorSessionRepository) {
        super(authCheckService, userRepository, irisSessionService, irisSettingsService, irisHealthIndicator, irisRateLimitService,
                programmingExerciseRepository::findByIdElseThrow);
        this.irisCodeEditorSessionRepository = irisCodeEditorSessionRepository;
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
    public ResponseEntity<IrisCodeEditorSession> getCurrentSession(@PathVariable Long exerciseId) {
        return super.getCurrentSession(exerciseId, IrisSubSettingsType.CODE_EDITOR, Role.EDITOR,
                (exercise, user) -> irisCodeEditorSessionRepository.findNewestByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId()));
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
        return super.getAllSessions(exerciseId, IrisSubSettingsType.CODE_EDITOR, Role.EDITOR,
                (exercise, user) -> irisCodeEditorSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId()));
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
    public ResponseEntity<IrisCodeEditorSession> createSessionForProgrammingExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        return super.createSessionForExercise(exerciseId, IrisSubSettingsType.CODE_EDITOR, Role.EDITOR, (exercise, user) -> {
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                return irisCodeEditorSessionRepository.save(new IrisCodeEditorSession(programmingExercise, user));
            }
            else {
                throw new ConflictException("Iris is only supported for programming exercises", "Iris", "irisProgrammingExercise");
            }
        });
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
        return ResponseEntity.ok(super.isIrisActiveInternal(session.getExercise(), session, IrisCombinedSettingsDTO::irisCodeEditorSettings).active());
    }
}
