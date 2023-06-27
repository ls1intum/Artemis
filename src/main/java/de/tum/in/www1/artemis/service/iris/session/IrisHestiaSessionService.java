package de.tum.in.www1.artemis.service.iris.session;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.iris.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service to handle the Hestia integration of Iris.
 */
@Service
@Profile("iris")
public class IrisHestiaSessionService implements IrisSessionSubServiceInterface {

    // TODO: Make configurable in future
    private static final String SYSTEM_PROMPT = """
            Your job is to add descriptions for code hints. You will be given a couple of code snippets and you have to add a single description for them. You'll also be given the name of the task that the code snippets are supposed to solve.
            The hints are supposed to help students solve an exercise by providing them with a part of the sample solution that is required to solve the exercise. The description should explain the code snippets and help the students understand what the code does and how they can use them.
            Only output the description. Do not output large parts of the code snippets. Do not just iterate through the code snippets (e.g. by saying "code snippet 1 does xyz, code snippet 2 does zyx", but actually explain what they do overall and how they relate to each other. You may reference the code snippets using their (shortened) file name and line numbers.
            Additionally in a second response you will be asked to provide a short description of the hint as a whole. This description should explain what the hint is supposed to help with without giving actual information about the solution.
            Use standard Markdown to format your descriptions (e.g. use # for headings, * for lists, `` for in line code, etc.).
            The first description should be at most 1000 characters long. The second description should be at most 100 characters long.
            """;

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

    /**
     * Generates the description and content for a code hint.
     * It does not directly save the code hint, but instead returns it with the generated description and content.
     * This way the instructor can still modify the code hint before saving it or discard the changes.
     *
     * @param codeHint The code hint to generate the description and content for
     * @return The code hint with the generated description and content
     */
    public CodeHint generateDescription(CodeHint codeHint) {
        var irisSession = new IrisHestiaSession();
        irisSession.setCodeHint(codeHint);
        checkHasAccessToIrisSession(irisSession, null);
        irisSession = irisSessionRepository.save(irisSession);

        var systemMessage = generateSystemMessage();
        var userMessage = generateUserMessage(codeHint);
        var irisMessages = List.of(systemMessage, userMessage);
        irisSession.getMessages().addAll(irisMessages);
        irisMessageService.saveMessage(systemMessage, irisSession, IrisMessageSender.ARTEMIS);
        irisMessageService.saveMessage(userMessage, irisSession, IrisMessageSender.USER);
        irisSession = (IrisHestiaSession) irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
        Map<String, Object> parameters = Map.of("codeHint", irisSession.getCodeHint());
        var irisSettings = irisSettingsService.getCombinedIrisSettings(irisSession.getCodeHint().getExercise(), false);
        try {
            var irisMessage1 = irisConnectorService
                    .sendRequest(irisSettings.getIrisHestiaSettings().getTemplate(), irisSettings.getIrisHestiaSettings().getPreferredModel(), parameters).get();
            irisMessageService.saveMessage(irisMessage1.message(), irisSession, IrisMessageSender.LLM);
            irisSession = (IrisHestiaSession) irisSessionRepository.findByIdWithMessagesAndContents(irisSession.getId());
            var irisMessage2 = irisConnectorService
                    .sendRequest(irisSettings.getIrisHestiaSettings().getTemplate(), irisSettings.getIrisHestiaSettings().getPreferredModel(), parameters).get();
            irisMessageService.saveMessage(irisMessage2.message(), irisSession, IrisMessageSender.LLM);

            codeHint.setContent(irisMessage1.message().getContent().stream().map(IrisMessageContent::getTextContent).collect(Collectors.joining("\n")));
            codeHint.setDescription(irisMessage2.message().getContent().stream().map(IrisMessageContent::getTextContent).collect(Collectors.joining("\n")));
            return codeHint;
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Unable to generate description", e);
            throw new InternalServerErrorException("Unable to generate description: " + e.getMessage());
        }
    }

    private IrisMessage generateSystemMessage() {
        var irisMessage = new IrisMessage();
        irisMessage.setSender(IrisMessageSender.ARTEMIS);
        irisMessage.setSentAt(ZonedDateTime.now());
        var irisMessageContent = new IrisMessageContent();
        irisMessageContent.setMessage(irisMessage);
        irisMessageContent.setTextContent(SYSTEM_PROMPT);
        irisMessage.setContent(List.of(irisMessageContent));
        return irisMessage;
    }

    private IrisMessage generateUserMessage(CodeHint codeHint) {
        var userPrompt = new StringBuilder(String.format("""
                ##Hint title: "%s"
                ## Task name: "%s"

                ## Code snippets:
                """, codeHint.getTitle(), codeHint.getProgrammingExerciseTask().getTaskName()));

        for (ProgrammingExerciseSolutionEntry solutionEntry : codeHint.getSolutionEntries()) {
            userPrompt.append(String.format("""
                    ### File: "%s"
                    ### Lines: %d to %d
                    ### Code:
                    ```
                    %s
                    ```
                    ----------
                    """, solutionEntry.getFilePath(), solutionEntry.getLine(), solutionEntry.getLine() + solutionEntry.getCode().split("\n").length - 1, solutionEntry.getCode()));
        }

        var irisMessage = new IrisMessage();
        irisMessage.setSender(IrisMessageSender.USER);
        irisMessage.setSentAt(ZonedDateTime.now());
        var irisMessageContent = new IrisMessageContent();
        irisMessageContent.setMessage(irisMessage);
        irisMessageContent.setTextContent(userPrompt.toString());
        irisMessage.setContent(List.of(irisMessageContent));
        return irisMessage;
    }

    /**
     * Not supported for Iris Hestia sessions.
     *
     * @param irisSession The session to get a message for
     */
    @Override
    public void requestAndHandleResponse(IrisSession irisSession) {
        throw new BadRequestException("Iris Hestia Session not supported");
    }

    /**
     * Checks if the user has at least the given role for the exercise of the code hint.
     *
     * @param irisSession The Iris session to check the access for
     * @param user        The user to check the access for
     */
    @Override
    public void checkHasAccessToIrisSession(IrisSession irisSession, User user) {
        var hestiaSession = castToSessionType(irisSession, IrisHestiaSession.class);
        var exercise = hestiaSession.getCodeHint().getExercise();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);
    }

    /**
     * Not supported for Iris Hestia sessions.
     *
     * @param session The session to get a message for
     */
    @Override
    public void checkIsIrisActivated(IrisSession session) {
        var irisHestiaSession = castToSessionType(session, IrisHestiaSession.class);
        irisSettingsService.checkIsIrisHestiaSessionEnabledElseThrow(irisHestiaSession.getCodeHint().getExercise());
    }
}
