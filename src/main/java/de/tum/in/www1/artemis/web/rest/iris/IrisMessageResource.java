package de.tum.in.www1.artemis.web.rest.iris;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link IrisMessage}.
 */
@RestController
@RequestMapping("api/iris/")
public class IrisMessageResource {

    private final Logger log = LoggerFactory.getLogger(IrisMessageResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final UserRepository userRepository;

    public IrisMessageResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            UserRepository userRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise
     */
    @GetMapping("sessions/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<IrisMessage>> getMessages(@PathVariable Long sessionId) {
        // IrisSession irisSession = irisSessionRepository.findByIdElseThrow(sessionId);
        var irisSession = new IrisSession();
        irisSession.setUser(userRepository.getUser());
        irisSession.setId(sessionId);
        // TODO: Session belongs to user
        // var result = irisMessageRepository.findBySessionId(irisSession.getId());
        var result = List.of(createMockMessage(irisSession, IrisMessageSender.LLM), createMockMessage(irisSession, IrisMessageSender.USER),
                createMockMessage(irisSession, IrisMessageSender.LLM), createMockMessage(irisSession, IrisMessageSender.USER),
                createMockMessage(irisSession, IrisMessageSender.LLM));
        return ResponseEntity.ok(result);
    }

    private IrisMessage createMockMessage(IrisSession session, IrisMessageSender sender) {
        var message = new IrisMessage();
        message.setId(ThreadLocalRandom.current().nextLong());
        message.setSender(sender);
        message.setHelpful(null);
        message.setSentAt(ZonedDateTime.now());
        message.setSession(session);
        message.setContent(List.of(createMockContent(message)));
        return message;
    }

    private IrisMessageContent createMockContent(IrisMessage message) {
        var content = new IrisMessageContent();
        content.setId(ThreadLocalRandom.current().nextLong());
        content.setMessage(message);
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        Random rand = new Random();
        String randomAdjective = adjectives[rand.nextInt(adjectives.length)];
        String randomNoun = nouns[rand.nextInt(nouns.length)];

        content.setTextContent("The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.");
        return content;
    }

    /**
     * POST session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise
     */
    @PostMapping("sessions/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisMessage> createMessage(@PathVariable Long sessionId, @RequestBody IrisMessage message) {
        // TODO: Save message and trigger request to LLM
        var irisSession = new IrisSession();
        irisSession.setUser(userRepository.getUser());
        irisSession.setId(sessionId);

        message.setId(ThreadLocalRandom.current().nextLong());
        message.setSession(irisSession);
        return ResponseEntity.ok(message);
    }

    /**
     * PUT session/{sessionId}/message: Retrieve the messages for the iris session.
     *
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise
     */
    @PutMapping("sessions/{sessionId}/messages/{messageId}/helpful/{helpful}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisMessage> rateMessage(@PathVariable Long sessionId, @PathVariable Long messageId, @PathVariable Boolean helpful) {
        // TODO: Load session, message and update helpfulness of message
        var irisSession = new IrisSession();
        irisSession.setUser(userRepository.getUser());
        irisSession.setId(sessionId);
        var mockMessage = createMockMessage(irisSession, IrisMessageSender.LLM);
        mockMessage.setId(messageId);
        mockMessage.setHelpful(helpful);

        return ResponseEntity.ok(mockMessage);
    }
}
