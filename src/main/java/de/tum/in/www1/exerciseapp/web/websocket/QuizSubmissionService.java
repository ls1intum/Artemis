package de.tum.in.www1.exerciseapp.web.websocket;

import de.tum.in.www1.exerciseapp.domain.*;
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
public class QuizSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionService.class);

    private static final Map<String, QuizSubmission> cachedSubmissions = new ConcurrentHashMap<>();

    private final SimpMessageSendingOperations messagingTemplate;

    public QuizSubmissionService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * generate the key for cached submissions using the username (for security reasons) and the submission id
     * @param username the username of the user who sent the submission
     * @param submissionId the id of the submission
     * @return the key used in the cachedSubmissions Map
     */
    private static String keyForUsernameAndSubmissionId(String username, Long submissionId) {
        return username + "_&_" + submissionId;
    }

    /**
     * Get the cached submission for the given username and submission id
     * @param username the username of the user who sent the submission
     * @param submissionId the id of the submission
     * @return The QuizSubmission object (answers saved by user through websocket)
     */
    public static QuizSubmission getCachedSubmission(String username, Long submissionId) {
        return cachedSubmissions.get(keyForUsernameAndSubmissionId(username, submissionId));
    }

    /**
     * Removes an entry from the cached submissions (should be used at the end of a quiz to free up memory)
     * @param username the username of the user who sent the submission
     * @param submissionId the id of the submission
     */
    public static void removeCachedSubmission(String username, Long submissionId) {
        cachedSubmissions.remove(keyForUsernameAndSubmissionId(username, submissionId));
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
        cachedSubmissions.put(keyForUsernameAndSubmissionId(principal.getName(), quizSubmission.getId()), quizSubmission);

        // send response (to all subscribers)
        messagingTemplate.convertAndSend("/topic/quizSubmissions/" + quizSubmission.getId(),
            "{\"saved\": \"" + ZonedDateTime.now().toString().substring(0, 23) + "\"}");
    }
}
