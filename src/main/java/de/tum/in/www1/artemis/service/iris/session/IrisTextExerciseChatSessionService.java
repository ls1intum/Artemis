package de.tum.in.www1.artemis.service.iris.session;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisTextExerciseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@Service
@Profile("iris")
public class IrisTextExerciseChatSessionService {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisMessageService irisMessageService;

    private final IrisRateLimitService rateLimitService;

    private final TextExerciseRepository textExerciseRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    public IrisTextExerciseChatSessionService(IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisMessageService irisMessageService,
            IrisRateLimitService rateLimitService, TextExerciseRepository textExerciseRepository, PyrisPipelineService pyrisPipelineService,
            IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService) {
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisMessageService = irisMessageService;
        this.rateLimitService = rateLimitService;
        this.textExerciseRepository = textExerciseRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
    }

    public IrisTextExerciseChatSession createTextExerciseSessionForUserAndExercise(User user, TextExercise exercise) {
        IrisTextExerciseChatSession session = new IrisTextExerciseChatSession(exercise, user);
        return irisSessionRepository.save(session);
    }

    /**
     * This method returns true if the user has access to the given session.
     * The user has access iff the user is a student in the exercise's course,
     * and they are the same user that created the session.
     *
     * @param user    The user to check
     * @param session The session to check
     * @return True if the user has access, false otherwise
     */
    public boolean hasAccess(User user, IrisTextExerciseChatSession session) {
        // TODO: This check is probably unnecessary since we are fetching the sessions from the database with the user ID already
        if (!session.getUser().equals(user)) {
            return false;
        }
        try {
            // TODO: This check is probably unnecessary as the endpoint already checks it via the @EnforceAtLeastStudentInExercise annotation
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, session.getExercise(), user);
        }
        catch (AccessForbiddenException e) {
            return false;
        }
        return true;
    }

    public void executePipelineForSession(IrisTextExerciseChatSession session) {
        var textExerciseSession = (IrisTextExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        if (textExerciseSession.getExercise().isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        // TODO: Figure out which DB call is needed here
        var exercise = textExerciseRepository.findWithGradingCriteriaById(textExerciseSession.getExercise().getId()).orElseThrow();
        if (!irisSettingsService.isEnabledFor(IrisSubSettingsType.TEXT_EXERCISE_CHAT, exercise)) {
            throw new ConflictException("Iris is not enabled for this exercise", "Iris", "irisDisabled");
        }
    }

}
