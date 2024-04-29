package de.tum.in.www1.artemis.service.iris.session;

import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.iris.message.IrisJsonMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.iris.IrisHestiaSessionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service to handle the Hestia integration of Iris.
 */
@Service
@Profile("iris")
public class IrisHestiaSessionService implements IrisButtonBasedFeatureInterface<IrisHestiaSession, CodeHint> {

    private static final Logger log = LoggerFactory.getLogger(IrisHestiaSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisHestiaSessionRepository irisHestiaSessionRepository;

    public IrisHestiaSessionService(IrisConnectorService irisConnectorService, IrisSettingsService irisSettingsService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, IrisHestiaSessionRepository irisHestiaSessionRepository) {
        this.irisConnectorService = irisConnectorService;
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

    // @formatter:off
    record HestiaDTO(
            CodeHint codeHint,
            IrisHestiaSession session,
            ProgrammingExercise exercise
    ) {}
    // @formatter:on

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
        var irisSession = irisHestiaSessionRepository.findWithMessagesAndContentsAndCodeHintById(session.getId());
        var codeHint = irisSession.getCodeHint();
        var parameters = new HestiaDTO(irisSession.getCodeHint(), irisSession, codeHint.getExercise());
        var settings = irisSettingsService.getCombinedIrisSettingsFor(irisSession.getCodeHint().getExercise(), false).irisHestiaSettings();
        try {
            var response = irisConnectorService.sendRequestV2(settings.getTemplate().getContent(), settings.getPreferredModel(), parameters).get();
            var shortDescription = response.content().get("shortDescription").asText();
            var longDescription = response.content().get("longDescription").asText();
            var llmMessage = irisSession.newMessage();
            llmMessage.setSender(IrisMessageSender.LLM);
            llmMessage.addContent(new IrisJsonMessageContent(response.content()));

            irisSessionRepository.save(irisSession);

            codeHint.setDescription(shortDescription);
            codeHint.setContent(longDescription);
            return codeHint;
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Unable to generate description", e);
            throw new InternalServerErrorException("Unable to generate description: " + e.getMessage());
        }
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
