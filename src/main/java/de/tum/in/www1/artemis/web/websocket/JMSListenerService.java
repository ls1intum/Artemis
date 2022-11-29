package de.tum.in.www1.artemis.web.websocket;

import java.io.IOException;

import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.jms.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.service.QuizSubmissionService;

@Service
@Profile("quiz")
public class JMSListenerService {

    private final ObjectMapper objectMapper;

    private final QuizSubmissionService quizSubmissionService;

    public JMSListenerService(ObjectMapper objectMapper, QuizSubmissionService quizSubmissionService) {
        this.objectMapper = objectMapper;
        this.quizSubmissionService = quizSubmissionService;
    }

    @Bean
    public SimpleMessageListenerContainer quizSubmissionMessageListener(final ConnectionFactory connectionFactory) {
        // Create an adapter for some service
        final MessageListenerAdapter messageListener = new MessageListenerAdapter(new AbstractAdaptableMessageListener() {

            @Override
            public void onMessage(javax.jms.Message message, Session session) throws JMSException {
                System.err.println(message);

                ActiveMQMessage activeMQMessage = (ActiveMQMessage) message;

                var bytesMessage = (BytesMessage) message;
                byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(bytes);
                try {
                    var quizSubmission = objectMapper.readValue(bytes, QuizSubmission.class);
                    var address = activeMQMessage.getCoreMessage().getAddress();
                    var exerciseId = Long.parseLong(address.split("/queue/quizExercise/")[1].split("/submission")[0]);
                    try {
                        quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, activeMQMessage.getStringProperty("user-name"), false);
                    }
                    catch (QuizSubmissionException e) {
                        e.printStackTrace();
                    }

                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        // Connect the method [1]
        // messageListener.setDefaultListenerMethod("saveSubmission");
        // Direct every outcome of "someNonVoidMethod" to a topic, that is
        // subscribable via stompClient.subscribe('/topic/some/response/topic', {});
        // [2] to a queue
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setDestinationName("/queue/quizExercise/*/submission");
        container.setMessageListener(messageListener);
        container.setConnectionFactory(connectionFactory);
        return container;
    }

}
