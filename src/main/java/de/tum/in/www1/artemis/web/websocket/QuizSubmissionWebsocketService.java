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
        long start = System.currentTimeMillis();
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();

        String username = principal.getName();
        // check if submission is still allowed
        Optional<QuizExercise> quizExercise = quizExerciseService.findById(exerciseId);
        if (quizExercise.isEmpty()) {
            return;
        }
        if (!quizExercise.get().isSubmissionAllowed()) {
            // notify the user that submission was not saved because quiz is not active over payload and handle this case in the client
            messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", "the quiz is not active");
            return;
        }

        // check if user already submitted for this quiz
        Participation participation = participationService.participationForQuizWithResult(quizExercise.get(), username);
        if (!participation.getResults().isEmpty()) {
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

        // send updated submission over websocket
        messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", quizSubmission);
        log.info("Save quiz submission for {} in {} ms in quiz {}", principal.getName(), System.currentTimeMillis() - start, quizExercise.get().getTitle());
    }
}
