package de.tum.in.www1.artemis.web.websocket;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;

@SuppressWarnings("unused")
@Controller
public class QuizSubmissionWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionWebsocketService.class);

    private final QuizExerciseService quizExerciseService;

    private final ParticipationService participationService;

    private final SimpMessageSendingOperations messagingTemplate;

    public QuizSubmissionWebsocketService(QuizExerciseService quizExerciseService, ParticipationService participationService, SimpMessageSendingOperations messagingTemplate) {
        this.quizExerciseService = quizExerciseService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
    }

    // TODO it would be nice to have some kind of startQuiz call that creates the participation with an initialization date. This should happen when the quiz is first shown
    // to the user. Then we also could find out how long students work on the quiz on average

    /**
     * Saves a Submission
     * @param exerciseId the exerciseID to the corresponding QuizExercise
     * @param quizSubmission the submission which should be saved
     * @param principal the current principal
     */
    @MessageMapping("/topic/quizExercise/{exerciseId}/submission")
    public void saveSubmission(@DestinationVariable Long exerciseId, @Payload QuizSubmission quizSubmission, Principal principal) {
        long start = System.nanoTime();
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();

        String username = principal.getName();
        // check if submission is still allowed
        QuizExercise quizExercise = QuizScheduleService.getQuizExercise(exerciseId);
        if (quizExercise == null) {
            // Fallback solution
            Optional<QuizExercise> optionalQuizExercise = quizExerciseService.findById(exerciseId);
            if (optionalQuizExercise.isEmpty()) {
                log.warn("Could not save quiz exercise for user {} in quiz {} because the quizExercise could not be found.", username, principal);
                return;
            }
            quizExercise = optionalQuizExercise.get();
        }
        log.info("WS.Inbound: Received quiz exercise for user {} in quiz {} in {} µs.", username, exerciseId, (System.nanoTime() - start) / 1000);
        if (!quizExercise.isSubmissionAllowed()) {
            // notify the user that submission was not saved because quiz is not active over payload and handle this case in the client
            log.warn("Quiz {} has ended. Cannot save submission for {}, took {} µs.", quizExercise.getTitle(), principal.getName(), (System.nanoTime() - start) / 1000);
            messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", "the quiz is not active");
            return;
        }

        // TODO: add one additional check: fetch quizSubmission.getId() with the corresponding participation and check that the user of participation is the
        // same as the user who executes this call. This prevents injecting submissions to other users

        // check if user already submitted for this quiz
        Participation participation = participationService.participationForQuizWithResult(quizExercise, username);
        log.info("WS.Inbound: Received participation for user {} in quiz {} in {} µs.", username, exerciseId, (System.nanoTime() - start) / 1000);
        if (!participation.getResults().isEmpty()) {
            log.debug("Participation for user {} in quiz {} has results", username, exerciseId);
            // NOTE: At this point, there can only be one Result because we already checked
            // if the quiz is active, so there is no way the student could have already practiced
            Result result = (Result) participation.getResults().toArray()[0];
            if (result.getSubmission().isSubmitted()) {
                // notify the user that submission was not saved because they already submitted over payload and handle this case in the client
                messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", "you have already submitted the quiz");
                return;
            }
        }

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        // set submission date
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // save submission to HashMap
        QuizScheduleService.updateSubmission(exerciseId, username, quizSubmission);

        log.info("WS.Inbound: Save quiz submission for user {} in quiz {} after {} µs ", principal.getName(), exerciseId, (System.nanoTime() - start) / 1000);
        // send updated submission over websocket (use a thread to prevent that the outbound channel blocks the inbound channel (e.g. due a slow client)
        new Thread(() -> sendSubmissionToUser(username, exerciseId, quizSubmission)).start();

        log.info("WS.Inbound: Sent quiz submission (async) back to user {} in quiz {} after {} µs ", principal.getName(), exerciseId, (System.nanoTime() - start) / 1000);
    }

    /**
     * Should be invoked using a thread asynchronously
     *
     * @param username the user who saved / submitted the quiz submission
     * @param exerciseId the quiz exercise id
     * @param quizSubmission the quiz submission that is returned back to the user
     */
    private void sendSubmissionToUser(String username, Long exerciseId, QuizSubmission quizSubmission) {
        long start = System.nanoTime();
        messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", quizSubmission);
        log.info("WS.Outbound: Sent quiz submission to user {} in quiz {} in {} µs ", username, exerciseId, (System.nanoTime() - start) / 1000);
    }
}
