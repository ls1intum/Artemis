package de.tum.in.www1.artemis.lecture.util;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import de.tum.in.www1.artemis.domain.Exercise;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Set;

@SpringBootTest
@Component
public class JmsMessageMockProvider {
    @Autowired
    private JmsTemplate jmsTemplate;

    @Mock
    private Message message;

    public JmsMessageMockProvider() {
        this.message = Mockito.mock(Message.class);
    }

    /**
     * Mock send and receive getting lecture exercises
     *
     * @param exercises the exercises to return as response
     * @throws JMSException
     */
    public void mockSendAndReceiveGetLectureExercises(Set<Exercise> exercises) throws JMSException {
        doReturn(message).when(jmsTemplate).receiveSelected(anyString(), anyString());
        doReturn(exercises).when(message).getBody(Set.class);
    }
}
