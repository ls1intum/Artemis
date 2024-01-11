package de.tum.in.www1.artemis.service.iris.session;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.iris.IrisHestiaSessionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

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

    private final IrisHestiaSessionRepository irisHestiaSessionRepository;

    public IrisHestiaSessionService(IrisConnectorService irisConnectorService, IrisMessageService irisMessageService, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository, IrisHestiaSessionRepository irisHestiaSessionRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisHestiaSessionRepository = irisHestiaSessionRepository;
    }

    public IrisHestiaSession getOrCreateSession(CodeHint codeHint) {
        var existingSessions = irisHestiaSessionRepository.findByCodeHintId(codeHint.getId());
        // Return the newest session if there is one and it is not older than 1 hour
        if (!existingSessions.isEmpty() && existingSessions.get(0).getCreationDate().plusHours(1).isAfter(ZonedDateTime.now())) {
            checkHasAccessTo(null, existingSessions.get(0));
            return existingSessions.get(0);
        }

        // Otherwise create a new session
        var irisSession = new IrisHestiaSession();
        irisSession.setCodeHint(codeHint);
        checkHasAccessTo(null, irisSession);
        irisSession = irisSessionRepository.save(irisSession);
        return irisSession;
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
        var irisSession = irisHestiaSessionRepository.findByIdWithMessagesAndContentsAndCodeHint(session.getId());
        var codeHint = irisSession.getCodeHint();
        Map<String, Object> parameters = Map.of("codeHint", irisSession.getCodeHint(), "session", irisSession, "exercise", codeHint.getExercise());
        var irisSettings = irisSettingsService.getCombinedIrisSettingsFor(irisSession.getCodeHint().getExercise(), false);
        try {
            var response = irisConnectorService
                    .sendRequestV2(irisSettings.irisHestiaSettings().getTemplate().getContent(), irisSettings.irisHestiaSettings().getPreferredModel(), parameters).get();
            var shortDescription = response.content().get("shortDescription").asText();
            var longDescription = response.content().get("longDescription").asText();
            var llmMessageContent = """
                    **Short description:**
                    %s
                    **Long description:**
                    %s
                    """.formatted(shortDescription, longDescription);
            var llmMessage = new IrisMessage();
            llmMessage.setSender(IrisMessageSender.LLM);
            llmMessage.addContent(new IrisTextMessageContent(llmMessageContent));
            irisMessageService.saveMessage(llmMessage, irisSession, IrisMessageSender.LLM);

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
