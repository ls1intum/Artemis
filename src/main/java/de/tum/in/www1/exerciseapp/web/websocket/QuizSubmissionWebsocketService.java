package de.tum.in.www1.exerciseapp.web.websocket;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.service.QuizSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class QuizSubmissionWebsocketService {
    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionWebsocketService.class);

    private final QuizSubmissionService quizSubmissionService;
    private final SimpMessageSendingOperations messagingTemplate;

    public QuizSubmissionWebsocketService(QuizSubmissionService quizSubmissionService,
                                          SimpMessageSendingOperations messagingTemplate) {
        this.quizSubmissionService = quizSubmissionService;
        this.messagingTemplate = messagingTemplate;
    }

    @SubscribeMapping("/topic/quizSubmissions/{submissionId}/save")
    public void sendSubmission(@Payload QuizSubmission quizSubmission, Principal principal) {
        log.info("Received Submission over Websocket: {}", quizSubmission);

        // do nothing if id is missing
        if (quizSubmission.getId() == null) {
            return;
        }

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        // set submission date for response
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // save submission in cache
        quizSubmissionService.setCachedSubmission(principal.getName(), quizSubmission);

        // send response (to all subscribers)
        messagingTemplate.convertAndSend("/topic/quizSubmissions/" + quizSubmission.getId(),
            "{\"saved\": \"" + ZonedDateTime.now().toString().substring(0, 23) + "\"}");
    }
}
