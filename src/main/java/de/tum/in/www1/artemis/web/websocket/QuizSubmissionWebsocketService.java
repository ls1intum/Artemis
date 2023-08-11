package de.tum.in.www1.artemis.web.websocket;

import java.security.Principal;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;

@Controller
@Profile("!decoupling || quiz")
public class QuizSubmissionWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionWebsocketService.class);

    private final QuizSubmissionService quizSubmissionService;

    private final WebsocketMessagingService websocketMessagingService;

    public QuizSubmissionWebsocketService(QuizSubmissionService quizSubmissionService, WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
        this.quizSubmissionService = quizSubmissionService;
    }

    // TODO it would be nice to have some kind of startQuiz call that creates the participation with an initialization date. This should happen when the quiz is first shown
    // to the user. Then we also could find out how long students work on the quiz on average

    /**
     * Saves a quiz submission into the hash maps. Submitted quizzes are marked to be saved into the database in the QuizScheduleService
     *
     * @param exerciseId     the exerciseID to the corresponding QuizExercise
     * @param quizSubmission the submission which should be saved
     * @param principal      refers to the user who initiated the request
     */
    @MessageMapping("/queue/quizExercise/{exerciseId}/submission")
    public void saveSubmission(@DestinationVariable Long exerciseId, @Valid @Payload QuizSubmission quizSubmission, Principal principal) {
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();
        try {
            QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, principal.getName(), false);
            // send updated submission over websocket (use a thread to prevent that the outbound channel blocks the inbound channel (e.g. due a slow client))
            // to improve the performance, this is currently deactivated: slow clients might lead to bottlenecks so that more important messages can not be distributed any more
            // new Thread(() -> sendSubmissionToUser(username, exerciseId, quizSubmission)).start();

            // log.info("WS.Inbound: Sent quiz submission (async) back to user {} in quiz {} after {} µs ", principal.getName(), exerciseId, (System.nanoTime() - start) / 1000);
        }
        catch (QuizSubmissionException ex) {
            // send error message over websocket (use Async to prevent that the outbound channel blocks the inbound channel (e.g. due a slow client))
            websocketMessagingService.sendMessageToUser(principal.getName(), "/topic/quizExercise/" + exerciseId + "/submission", new WebsocketError(ex.getMessage()));
        }
    }

    /**
     * Should be invoked using a thread asynchronously
     *
     * @param username       the user who saved / submitted the quiz submission
     * @param exerciseId     the quiz exercise id
     * @param quizSubmission the quiz submission that is returned back to the user
     */
    private void sendSubmissionToUser(String username, Long exerciseId, QuizSubmission quizSubmission) {
        long start = System.nanoTime();
        websocketMessagingService.sendMessageToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", quizSubmission);
        log.info("WS.Outbound: Sent quiz submission to user {} in quiz {} in {} µs ", username, exerciseId, (System.nanoTime() - start) / 1000);
    }
}
