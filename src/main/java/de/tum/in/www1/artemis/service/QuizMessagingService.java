package de.tum.in.www1.artemis.service;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;

@Service
public class QuizMessagingService {

    private final Logger log = LoggerFactory.getLogger(QuizMessagingService.class);

    private final ObjectMapper objectMapper;

    private final GroupNotificationService groupNotificationService;

    private final SimpMessageSendingOperations messagingTemplate;

    public QuizMessagingService(MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter, GroupNotificationService groupNotificationService,
            SimpMessageSendingOperations messagingTemplate) {
        this.objectMapper = mappingJackson2HttpMessageConverter.getObjectMapper();
        this.groupNotificationService = groupNotificationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a QuizExercise to all subscribed clients and creates notification if quiz has started.
     * @param quizExercise the QuizExercise which will be sent
     * @param quizBatch the batch that has been started
     * @param quizChange the change that was applied to the quiz, which decides to which topic subscriptions the quiz exercise is sent
     */
    public void sendQuizExerciseToSubscribedClients(QuizExercise quizExercise, @Nullable QuizBatch quizBatch, String quizChange) {
        try {
            long start = System.currentTimeMillis();
            Class<?> view = quizExercise.viewForStudentsInQuizExercise(quizBatch);
            byte[] payload = objectMapper.writerWithView(view).writeValueAsBytes(quizExercise);
            // For each change we send the same message. The client needs to decide how to handle the date based on the quiz status
            if (quizExercise.isVisibleToStudents() && quizExercise.isCourseExercise()) {
                // Create a group notification if actions is 'start-now'.
                if ("start-now".equals(quizChange)) {
                    groupNotificationService.notifyStudentGroupAboutQuizExerciseStart(quizExercise);
                }
                // Send quiz via websocket.
                String destination = "/topic/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quizExercises";
                if ("start-batch".equals(quizChange) && quizBatch != null) {
                    destination = destination + "/" + quizBatch.getId();
                }
                messagingTemplate.send(destination, MessageBuilder.withPayload(payload).build());
                log.info("Sent '{}' for quiz {} to all listening clients in {} ms", quizChange, quizExercise.getId(), System.currentTimeMillis() - start);
            }
        }
        catch (JsonProcessingException e) {
            log.error("Exception occurred while serializing quiz exercise", e);
        }
    }
}
