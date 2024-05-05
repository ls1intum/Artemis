package de.tum.in.www1.artemis.web.websocket;

import static de.tum.in.www1.artemis.config.Constants.NEW_SELF_LEARNING_FEEDBACK;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.SelfLearningFeedbackRequest;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * This service is responsible for sending websocket notifications when there is an update of self-learning-feedback
 */
@Service
@Profile(PROFILE_CORE)
public class SelfLearningFeedbackWebsocketService {

    private final WebsocketMessagingService websocketMessagingService;

    public SelfLearningFeedbackWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    public void broadcastNewSelfLearningRequest(StudentParticipation studentParticipation, SelfLearningFeedbackRequest selfLearningFeedbackRequest) {
        final Exercise exercise = studentParticipation.getExercise();
        if (exercise.isExamExercise() || ZonedDateTime.now().isAfter(exercise.getDueDate()))
            return;

        var students = studentParticipation.getStudents();

        students.stream().forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), NEW_SELF_LEARNING_FEEDBACK, selfLearningFeedbackRequest));
    }
}
