package de.tum.cit.aet.artemis.service.iris.session;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.hestia.CodeHint;
import de.tum.cit.aet.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.repository.iris.IrisHestiaSessionRepository;
import de.tum.cit.aet.artemis.repository.iris.IrisSessionRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.connectors.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.service.iris.settings.IrisSettingsService;

/**
 * Service to handle the Hestia integration of Iris.
 */
@Service
@Profile("iris")
public class IrisHestiaSessionService implements IrisButtonBasedFeatureInterface<IrisHestiaSession, CodeHint> {

    private static final Logger log = LoggerFactory.getLogger(IrisHestiaSessionService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisHestiaSessionRepository irisHestiaSessionRepository;

    public IrisHestiaSessionService(PyrisConnectorService pyrisConnectorService, IrisSettingsService irisSettingsService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisHestiaSessionRepository irisHestiaSessionRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisHestiaSessionRepository = irisHestiaSessionRepository;
    }

    /**
     * Creates a new Iris session for the given code hint.
     * If there is already an existing session for the code hint from the last hour, it will be returned instead.
     *
     * @param codeHint The code hint to create the session for
     * @return The Iris session for the code hint
     */
    public IrisHestiaSession getOrCreateSession(CodeHint codeHint) {
        var existingSessions = irisHestiaSessionRepository.findByCodeHintIdOrderByCreationDateDesc(codeHint.getId());
        // Return the newest session if there is one and it is not older than 1 hour
        if (!existingSessions.isEmpty() && existingSessions.getFirst().getCreationDate().plusHours(1).isAfter(ZonedDateTime.now())) {
            checkHasAccessTo(null, existingSessions.getFirst());
            return existingSessions.getFirst();
        }

        // Otherwise create a new session
        var irisSession = new IrisHestiaSession();
        irisSession.setCodeHint(codeHint);
        checkHasAccessTo(null, irisSession);
        irisSession = irisSessionRepository.save(irisSession);
        return irisSession;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record HestiaDTO(CodeHint codeHint, IrisHestiaSession session, ProgrammingExercise exercise) {
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
        // TODO: Re-add in a future PR. Remember to reenable the test cases!
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
