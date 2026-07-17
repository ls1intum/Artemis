package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.quiz.util.QuizJsonNodeTestUtil.findQuestionByType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.Message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.quiz.domain.QuizAction;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

class QuizMessagingServiceTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void shouldOmitSolutionsWhenSendingStartedBatchPayload() throws Exception {
        WebsocketMessagingService websocketMessagingService = mock(WebsocketMessagingService.class);
        QuizMessagingService quizMessagingService = new QuizMessagingService(new MappingJackson2HttpMessageConverter(objectMapper), mock(GroupNotificationService.class),
                websocketMessagingService);
        QuizExercise quizExercise = createActiveQuiz();
        QuizBatch quizBatch = new QuizBatch();
        quizBatch.setId(17L);
        quizBatch.setStartTime(ZonedDateTime.now().minusMinutes(1));
        quizBatch.setQuizExercise(quizExercise);

        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, quizBatch, QuizAction.START_BATCH);

        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.captor();
        verify(websocketMessagingService).sendMessage(eq("/topic/courses/5/quizExercises/17"), messageCaptor.capture());
        Message<?> message = messageCaptor.getValue();
        JsonNode payload = objectMapper.readTree((byte[]) message.getPayload());
        assertThat(payload.path("quizQuestions")).hasSize(3);

        JsonNode multipleChoiceQuestion = findQuestionByType(payload.path("quizQuestions"), "multiple-choice");
        JsonNode dragAndDropQuestion = findQuestionByType(payload.path("quizQuestions"), "drag-and-drop");
        JsonNode shortAnswerQuestion = findQuestionByType(payload.path("quizQuestions"), "short-answer");
        assertThat(multipleChoiceQuestion.path("answerOptions").get(0).get("isCorrect")).isNull();
        assertThat(dragAndDropQuestion.get("correctMappings")).isNull();
        assertThat(shortAnswerQuestion.get("solutions")).isNull();
        assertThat(shortAnswerQuestion.get("correctMappings")).isNull();
    }

    private static QuizExercise createActiveQuiz() {
        ZonedDateTime now = ZonedDateTime.now();
        Course course = CourseFactory.generateCourse(5L, now.minusDays(1), now.plusDays(1), Set.of());
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(now.minusMinutes(5), now.plusMinutes(30), QuizMode.BATCHED, course);
        QuizExerciseFactory.addQuestionsToQuizExercise(quizExercise);
        quizExercise.setId(11L);
        return quizExercise;
    }
}
