package de.tum.cit.aet.artemis.iris.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisTextExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTextExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * REST controller for managing {@link IrisTextExerciseChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/text-exercise-chat/")
public class IrisTextExerciseChatSessionResource {

    private final UserRepository userRepository;

    private final IrisSessionService irisSessionService;

    private final IrisSettingsService irisSettingsService;

    private final TextExerciseRepository textExerciseRepository;

    private final IrisTextExerciseChatSessionService irisTextExerciseChatSessionService;

    private final IrisTextExerciseChatSessionRepository irisTextExerciseChatSessionRepository;

    protected IrisTextExerciseChatSessionResource(IrisTextExerciseChatSessionRepository irisTextExerciseChatSessionRepository, UserRepository userRepository,
            TextExerciseRepository textExerciseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            IrisTextExerciseChatSessionService irisTextExerciseChatSessionService) {
        this.irisTextExerciseChatSessionRepository = irisTextExerciseChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.textExerciseRepository = textExerciseRepository;
        this.irisTextExerciseChatSessionService = irisTextExerciseChatSessionService;
    }

    /**
     * GET exercise-chat/{exerciseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{exerciseId}/sessions/current")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisTextExerciseChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.TEXT_EXERCISE_CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var sessionOptional = irisTextExerciseChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId(), Pageable.ofSize(1)).stream()
                .findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            irisSessionService.checkHasAccessToIrisSession(session, user);
            return ResponseEntity.ok(session);
        }

        return createSessionForExercise(exerciseId);
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
    public ResponseEntity<IrisTextExerciseChatSession> createSessionForExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        var textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        validateExercise(textExercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.TEXT_EXERCISE_CHAT, textExercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedIrisElseThrow();

        var session = irisTextExerciseChatSessionRepository.save(new IrisTextExerciseChatSession(textExercise, user));
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * GET exercise-chat/{exerciseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<List<IrisTextExerciseChatSession>> getAllSessions(@PathVariable Long exerciseId) {
        var exercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.TEXT_EXERCISE_CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedIrisElseThrow();

        var sessions = irisTextExerciseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        // TODO: Discuss this with the team: should we filter out sessions where the user does not have access, or throw an exception?
        // Access check might not even be necessary here -> see comments in hasAccess method
        var filteredSessions = sessions.stream().filter(session -> irisTextExerciseChatSessionService.hasAccess(user, session)).toList();
        return ResponseEntity.ok(filteredSessions);
    }

    private static void validateExercise(TextExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
    }
}
