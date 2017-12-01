package de.tum.in.www1.exerciseapp.web.websocket;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.repository.QuizExerciseRepository;
import de.tum.in.www1.exerciseapp.repository.QuizSubmissionRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import de.tum.in.www1.exerciseapp.web.websocket.dto.ActivityDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static de.tum.in.www1.exerciseapp.config.WebsocketConfiguration.IP_ADDRESS;

@Controller
public class QuizSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionService.class);

    private final SimpMessageSendingOperations messagingTemplate;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final ResultRepository resultRepository;

    public QuizSubmissionService(SimpMessageSendingOperations messagingTemplate, QuizSubmissionRepository quizSubmissionRepository, ResultRepository resultRepository) {
        this.messagingTemplate = messagingTemplate;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    @SubscribeMapping("/topic/quizSubmissions/{submissionId}/save")
    public void sendSubmission(@Payload QuizSubmission quizSubmission, Principal principal) {
        log.debug("Received Submission over Websocket: {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() == null) {
            return;
        }

        // update corresponding result
        Optional<Result> resultOptional = resultRepository.findDistinctBySubmissionId(quizSubmission.getId());
        if (resultOptional.isPresent()) {
            Result result = resultOptional.get();
            Participation participation = result.getParticipation();
            QuizExercise quizExercise = (QuizExercise) participation.getExercise();
            User user = participation.getStudent();
            // check if participation (and thus submission) actually belongs to the user who sent this message
            if (principal.getName().equals(user.getLogin())) {
                // only update if exercise hasn't ended already
                if (quizExercise.isSubmissionAllowed() && participation.getInitializationState() == ParticipationState.INITIALIZED) {
                    // save changes to submission
                    quizSubmission = quizSubmissionRepository.save(quizSubmission);

                    // update completion date (which also functions as submission date for now)
                    result.setCompletionDate(ZonedDateTime.now());
                    resultRepository.save(result);
                } else {
                    // overwrite with existing submission to send back unchanged submission
                    quizSubmission = (QuizSubmission) result.getSubmission();
                    // remove scores from submission if quiz hasn't ended yet
                    if (quizSubmission.isSubmitted() && quizExercise.shouldFilterForStudents()) {
                        quizSubmission.removeScores();
                    }
                }
                // set submission date for response
                quizSubmission.setSubmissionDate(result.getCompletionDate());
                // send response (to all subscribers)
                messagingTemplate.convertAndSend("/topic/quizSubmissions/" + quizSubmission.getId(), quizSubmission);
            }
        }
    }
}
