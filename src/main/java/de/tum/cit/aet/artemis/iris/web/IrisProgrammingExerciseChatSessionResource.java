package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for managing {@link IrisProgrammingExerciseChatSession}.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/programming-exercise-chat/")
public class IrisProgrammingExerciseChatSessionResource {

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final ProgrammingExerciseRepository exerciseRepository;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    protected IrisProgrammingExerciseChatSessionResource(IrisExerciseChatSessionRepository irisExerciseChatSessionRepository, UserRepository userRepository,
            ProgrammingExerciseRepository exerciseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService, IrisExerciseChatSessionService irisExerciseChatSessionService) {
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.exerciseRepository = exerciseRepository;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
    }

    /**
     * GET exercise-chat/{exerciseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{exerciseId}/sessions/current")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisProgrammingExerciseChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.ensureEnabledForCourseOrElseThrow(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var session = irisExerciseChatSessionService.getCurrentSessionOrCreateIfNotExists(programmingExercise, user, false);
        return ResponseEntity.ok(session);
    }

    /**
     * GET exercise-chat/{exerciseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<List<IrisProgrammingExerciseChatSession>> getAllSessions(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.ensureEnabledForCourseOrElseThrow(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var sessions = irisExerciseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST exercise-chat/{exerciseId}/session: Create a new iris session for an exercise and user.
     * If there already exists an iris session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisProgrammingExerciseChatSession> createSessionForExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.ensureEnabledForCourseOrElseThrow(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var session = irisExerciseChatSessionService.createSession(programmingExercise, user, false);
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    private static ProgrammingExercise validateExercise(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return exercise;
    }
}
