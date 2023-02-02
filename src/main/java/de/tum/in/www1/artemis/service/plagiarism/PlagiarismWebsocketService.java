package de.tum.in.www1.artemis.service.plagiarism;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.PlagiarismCheckState;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class PlagiarismWebsocketService {

    private final Logger log = LoggerFactory.getLogger(PlagiarismWebsocketService.class);

    private final WebsocketMessagingService websocketMessagingService;

    public PlagiarismWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /***
     * Sends a message notifying the user about the current state of the plagiarism check
     *
     * @param topic The topic to send the notification to
     * @param plagiarismCheckState The plagiarism check state
     * @param messages  optional messages to send
     */
    public void notifyInstructorAboutPlagiarismState(String topic, PlagiarismCheckState plagiarismCheckState, List<String> messages) {
        Map<String, String> payload = new HashMap<>();
        payload.put("state", plagiarismCheckState.toString());
        payload.put("messages", String.join("\n", messages));

        ObjectMapper mapper = new ObjectMapper();
        try {
            websocketMessagingService.sendMessage(topic, mapper.writeValueAsString(payload));
        }
        catch (IOException e) {
            log.info("Couldn't notify the user about the plagiarism state for topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Return the topic of the plagiarism check for the programming exercise
     * @param programmingExerciseId the id of the exercise
     * @return the topic
     */
    public String getProgrammingExercisePlagiarismCheckTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/plagiarism-check";
    }

    /**
     * Return the topic of the plagiarism check for the text exercise
     * @param textExerciseId the id of the exercise
     * @return the topic
     */
    public String getTextExercisePlagiarismCheckTopic(Long textExerciseId) {
        return "/topic/text-exercises/" + textExerciseId + "/plagiarism-check";
    }

    /**
     * Return the topic of the plagiarism check for the modeling exercise
     * @param modelingExerciseId the id of the exercise
     * @return the topic
     */
    public String getModelingExercisePlagiarismCheckTopic(Long modelingExerciseId) {
        return "/topic/modeling-exercises/" + modelingExerciseId + "/plagiarism-check";
    }
}
