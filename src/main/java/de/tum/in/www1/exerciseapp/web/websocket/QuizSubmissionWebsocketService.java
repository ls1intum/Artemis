package de.tum.in.www1.exerciseapp.web.websocket;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.ZonedDateTime;

@SuppressWarnings("unused")
@Controller
public class QuizSubmissionWebsocketService {
    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionWebsocketService.class);

    private final QuizExerciseService quizExerciseService;
    private final ParticipationService participationService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final UserService userService;
    private final AuthorizationCheckService authCheckService;

    public QuizSubmissionWebsocketService(QuizExerciseService quizExerciseService,
                                          ParticipationService participationService,
                                          SimpMessageSendingOperations messagingTemplate,
                                          UserService userService,
                                          AuthorizationCheckService authCheckService) {
        this.quizExerciseService = quizExerciseService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    @MessageMapping("/topic/quizExercise/{exerciseId}/submission")
    public void saveSubmission(@DestinationVariable Long exerciseId, @Payload QuizSubmission quizSubmission, Principal principal) {
        String username = principal.getName();

        // check if submission is still allowed
        QuizExercise quizExercise = quizExerciseService.findOne(exerciseId);
        if (!quizExercise.isSubmissionAllowed()) {
            // TODO: notify user that submission was not saved because quiz is not active
            messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", null);
            return;
        }

        // check if user is allowed to participate in this quiz
        User user = userService.getUserWithGroupsAndAuthoritiesByLogin(username);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, user)) {
            // TODO: notify user that they are not allowed to participate in this quiz
            messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", null);
            return;
        }

        // check if user already submitted for this quiz
        Participation participation = participationService.getParticipationForQuiz(quizExercise, username);
        if (!participation.getResults().isEmpty()) {
            // NOTE: At this point, there can only be one Result because we already checked
            // if the quiz is active, so there is no way the student could have already practiced
            Result result = (Result) participation.getResults().toArray()[0];
            if (result.getSubmission().isSubmitted()) {
                // TODO: notify user that submission was not saved because they already submitted
                messagingTemplate.convertAndSendToUser(username, "/topic/quizExercise/" + exerciseId + "/submission", null);
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
    }
}
