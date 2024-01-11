package de.tum.in.www1.artemis.service.iris.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

/**
 * Service to handle the Hestia integration of Iris.
 */
@Service
@Profile("iris")
public class IrisHestiaSessionService implements IrisButtonBasedFeatureInterface<IrisHestiaSession, CodeHint> {

    private final Logger log = LoggerFactory.getLogger(IrisHestiaSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    public IrisHestiaSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
    }

    public IrisHestiaSession getOrCreateSession(CodeHint codeHint) {
        // TODO: Follow up PR
        return null;
    }

    /**
     * Generates the description and content for a code hint.
     * It does not directly save the code hint, but instead returns it with the generated description and content.
     * This way the instructor can still modify the code hint before saving it or discard the changes.
     *
     * @param session The Iris session to generate the description for
     * @return The code hint with the generated description and content
     */
    @Override
    public CodeHint executeRequest(IrisHestiaSession session) {
        // TODO: Follow up PR
        return null;
    }

    /**
     * Checks if the user has at least the given role for the exercise of the code hint.
     *
     * @param user    The user to check the access for
     * @param session The Iris session to check the access for
     */
    @Override
    public void checkHasAccessTo(User user, IrisHestiaSession session) {
        var exercise = session.getCodeHint().getExercise();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);
    }

    /**
     * Not supported for Iris Hestia sessions.
     *
     * @param session The session to get a message for
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisHestiaSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.HESTIA, session.getCodeHint().getExercise());
    }
}
