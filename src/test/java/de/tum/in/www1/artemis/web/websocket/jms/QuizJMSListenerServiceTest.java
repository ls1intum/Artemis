package de.tum.in.www1.artemis.web.websocket.jms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import javax.jms.JMSException;

import org.junit.jupiter.api.Test;

/**
 * Note: We currently can not test the QuizJMSListenerService using a normal Spring bean, because an ActiveMQ-broker would be required,
 * which we can not create inside the testing environment.
 * We can therefor only test static methods right now.
 */
class QuizJMSListenerServiceTest {

    @Test
    void testExtractQuizExerciseIdFromAddress() throws JMSException {
        assertThat(QuizJMSListenerService.extractExerciseIdFromAddress("/queue/quizExercise/123/submission")).isEqualTo(123);

        assertThatExceptionOfType(JMSException.class).isThrownBy(() -> QuizJMSListenerService.extractExerciseIdFromAddress("/queue/quizExercise/abc/submission"));
        assertThatExceptionOfType(JMSException.class).isThrownBy(() -> QuizJMSListenerService.extractExerciseIdFromAddress("/queue/some/other/address"));
    }

}
