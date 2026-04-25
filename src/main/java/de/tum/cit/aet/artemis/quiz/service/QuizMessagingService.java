package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.quiz.domain.QuizAction.START_BATCH;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.quiz.domain.QuizAction;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizMessagingService {

    private static final Logger log = LoggerFactory.getLogger(QuizMessagingService.class);

    private final ObjectMapper objectMapper;

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    public QuizMessagingService(ObjectMapper objectMapper, GroupNotificationService groupNotificationService, WebsocketMessagingService websocketMessagingService) {
        this.objectMapper = objectMapper;
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a QuizExercise to all subscribed clients and creates notification if quiz has started.
     *
     * @param quizExercise the QuizExercise which will be sent
     * @param quizBatch    the batch that has been started
     * @param quizChange   the change that was applied to the quiz, which decides to which topic subscriptions the quiz exercise is sent
     */
    public void sendQuizExerciseToSubscribedClients(QuizExercise quizExercise, @Nullable QuizBatch quizBatch, QuizAction quizChange) {
        try {
            long start = System.currentTimeMillis();
            Object exerciseDTO;
            if (quizExercise.isQuizEnded()) {
                exerciseDTO = QuizExerciseWithSolutionDTO.of(quizExercise);
            }
            else if (quizBatch != null && quizBatch.isStarted()) {
                exerciseDTO = QuizExerciseWithQuestionsDTO.of(quizExercise);
            }
            else {
                exerciseDTO = QuizExerciseWithoutQuestionsDTO.of(quizExercise);
            }
            byte[] payload = objectMapper.writeValueAsBytes(exerciseDTO);
            // For each change we send the same message. The client needs to decide how to handle the date based on the quiz status
            if (quizExercise.isVisibleToStudents() && quizExercise.isCourseExercise()) {
                var course = quizExercise.getCourseViaExerciseGroupOrCourseMember();
                // Create a group notification if actions is 'start-now'. The fan-out to (potentially thousands of)
                // students runs asynchronously so it does not block the HTTP response of the lifecycle action that
                // triggered this broadcast (see GroupNotificationService#notifyStudentGroupAboutQuizExerciseStartAsync).
                // Primitives are resolved here on the caller's session before the async handoff. The surrounding
                // isCourseExercise() guard already excludes exam exercises, so no extra exam check is needed here.
                if (quizChange == QuizAction.START_NOW) {
                    groupNotificationService.notifyStudentGroupAboutQuizExerciseStartAsync(course.getId(), course.getTitle(), course.getCourseIcon(), course.getStudentGroupName(),
                            quizExercise.getId(), quizExercise.getExerciseNotificationTitle());
                }
                // Send quiz via websocket.
                String destination = "/topic/courses/" + course.getId() + "/quizExercises";
                if (quizChange == START_BATCH && quizBatch != null) {
                    destination = destination + "/" + quizBatch.getId();
                }
                // TODO the view could also be passed as conversion hint to the message converter
                websocketMessagingService.sendMessage(destination, MessageBuilder.withPayload(payload).build());
                log.info("Sent '{}' for quiz {} to all listening clients in {} ms", quizChange, quizExercise.getId(), System.currentTimeMillis() - start);
            }
        }
        catch (JsonProcessingException e) {
            log.error("Exception occurred while serializing quiz exercise", e);
        }
    }

}
