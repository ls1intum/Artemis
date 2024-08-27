package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisTextExerciseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

public class IrisTextExerciseChatSessionService {

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisMessageService irisMessageService;

    private final IrisRateLimitService rateLimitService;

    private final TextExerciseRepository textExerciseRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    public IrisTextExerciseChatSessionService(IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository, IrisMessageService irisMessageService,
            IrisRateLimitService rateLimitService, TextExerciseRepository textExerciseRepository, PyrisPipelineService pyrisPipelineService,
            IrisChatWebsocketService irisChatWebsocketService) {
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisMessageService = irisMessageService;
        this.rateLimitService = rateLimitService;
        this.textExerciseRepository = textExerciseRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.irisChatWebsocketService = irisChatWebsocketService;
    }

    public IrisTextExerciseChatSession createTextExerciseSessionForUserAndExercise(User user, TextExercise exercise) {
        IrisTextExerciseChatSession session = new IrisTextExerciseChatSession(exercise, user);
        return irisSessionRepository.save(session);
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
