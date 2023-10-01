package de.tum.in.www1.artemis.web.websocket.jms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import javax.jms.JMSException;

import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.listener.SimpleMessageListenerContainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;

class QuizJMSListenerServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    QuizExerciseRepository quizExerciseRepository;

    @Autowired
    QuizJMSListenerService quizJMSListenerService;

    static EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker();

    @BeforeAll
    static void startBroker() {
        broker.start();
    }

    @AfterAll
    static void stopBroker() {
        broker.stop();
    }

    @Test
    void testCallsQuizSubmission() throws JsonProcessingException, QuizSubmissionException, JMSException {
        var quizExercise = createQuizExercise();

        var connectionFactory = broker.createConnectionFactory();
        SimpleMessageListenerContainer simpleMessageListenerContainer = quizJMSListenerService.quizSubmissionMessageListener(connectionFactory,
                "/queue/quizExercise/" + quizExercise.getId() + "/submission");
        simpleMessageListenerContainer.start();

        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, false, null);

        var message = broker.createBytesMessage();
        message.writeBytes(objectMapper.writeValueAsBytes(quizSubmission));
        message.setStringProperty("user-name", "user-name1");

        broker.pushMessage("/queue/quizExercise/" + quizExercise.getId() + "/submission", message);

        verify(quizSubmissionService, timeout(1000)).saveSubmissionForLiveMode(eq(quizExercise.getId()), any(QuizSubmission.class), eq("user-name1"), eq(false));
    }

    private QuizExercise createQuizExercise() {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusMinutes(1), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(240);
        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    @Test
    void testExtractQuizExerciseIdFromAddress() throws JMSException {
        assertThat(QuizJMSListenerService.extractExerciseIdFromAddress("/queue/quizExercise/123/submission")).isEqualTo(123);
        assertThat(QuizJMSListenerService.extractExerciseIdFromAddress("queue:///queue/quizExercise/123/submission")).isEqualTo(123);

        assertThatExceptionOfType(JMSException.class).isThrownBy(() -> QuizJMSListenerService.extractExerciseIdFromAddress("/queue/quizExercise/abc/submission"));
        assertThatExceptionOfType(JMSException.class).isThrownBy(() -> QuizJMSListenerService.extractExerciseIdFromAddress("/queue/some/other/address"));
    }

}
