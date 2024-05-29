package de.tum.in.www1.artemis.web.websocket;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.quiz.QuizSubmissionService;

@Controller
@Profile(PROFILE_CORE)
public class QuizSubmissionWebsocketService {

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
    @MessageMapping("topic/quizExercise/{exerciseId}/submission")
    public void saveSubmission(@DestinationVariable Long exerciseId, @Valid @Payload QuizSubmission quizSubmission, Principal principal) { // TODO: Deprecate?
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();
        try {
            quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, principal.getName(), false);
        }
        catch (QuizSubmissionException ex) {
            // send error message over websocket (use Async to prevent that the outbound channel blocks the inbound channel (e.g. due a slow client))
            websocketMessagingService.sendMessageToUser(principal.getName(), "/topic/quizExercise/" + exerciseId + "/submission", new WebsocketError(ex.getMessage()));
        }
    }
}
