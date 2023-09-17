package de.tum.in.www1.artemis.web.websocket.jms;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.jetbrains.annotations.NotNull;
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

/**
 * The QuizJMSListenerService processes JMS messages that could not be processed by the Artemis instance that received the WebSocket Message
 * and forwarded it to the message broker.
 * <p>
 *
 * The message broker uses a queue, which ensures that only one instance that provides a JMS listener will process the message.
 */
@Service
@Profile("decoupling && quiz")
public class QuizJMSListenerService {

    private static final Pattern EXERCISE_ID_TOPIC_EXTRACTION_PATTERN = Pattern.compile("/queue/quizExercise/(\\d*)/submission");

    private final ObjectMapper objectMapper;

    private final QuizSubmissionService quizSubmissionService;

    public QuizJMSListenerService(ObjectMapper objectMapper, QuizSubmissionService quizSubmissionService) {
        this.objectMapper = objectMapper;
        this.quizSubmissionService = quizSubmissionService;
    }

    /**
     * Spring will provide the ConnectionFactory which uses the following configuration variables.<br>
     * - spring.artemis.broker-url: "tcp://BROKER-HOST:61616"<br>
     * - spring.artemis.user<br>
     * - spring.artemis.password<br>
     *
     * @param connectionFactory the connection factory provided by Spring
     * @return the SimpleMessageListenerContainer that will be used by Spring to register the topic
     */
    @Bean
    public SimpleMessageListenerContainer quizSubmissionMessageListener(final ConnectionFactory connectionFactory) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        // Set the name of the queue that should be processed by the message listener
        container.setDestinationName("/queue/quizExercise/*/submission");
        // Set the message listener that will be invoked when a new message is received
        container.setMessageListener(getQuizMessageListenerAdapter());
        container.setConnectionFactory(connectionFactory);

        return container;
    }

    /**
     * Create an adapter that receives JMS messages and triggers to corresponding logic.
     *
     * @return the adapter that receives the messages and processes the requests
     */
    private MessageListenerAdapter getQuizMessageListenerAdapter() {
        return new MessageListenerAdapter(new AbstractAdaptableMessageListener() {

            @Override
            public void onMessage(javax.jms.@NotNull Message message, Session session) throws JMSException {
                // Message is of type ActiveMQMessage because we use Apache ActiveMQ as broker
                ActiveMQBytesMessage activeMQMessage = (ActiveMQBytesMessage) message;

                var messageLength = activeMQMessage.getBodyLength();
                byte[] bytes = new byte[(int) messageLength];
                // Read message into bytes[]-array
                activeMQMessage.readBytes(bytes);

                try {
                    var quizSubmission = objectMapper.readValue(bytes, QuizSubmission.class);
                    var address = activeMQMessage.getCoreMessage().getAddress();
                    var username = JMSListenerUtils.extractUsernameFromMessage(message);
                    var exerciseId = extractExerciseIdFromAddress(address);
                    quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, username, false);
                }
                catch (JMSException e) {
                    logger.warn(String.format("Received JMSException: %s", e));
                }
                catch (QuizSubmissionException e) {
                    logger.warn(String.format("Could not process Quiz Submission: %s", e));
                }
                catch (IOException e) {
                    logger.warn(String.format("Could not process read JMS message: %s", e));
                }
            }
        });
    }

    /**
     * Extract the exercise id from the provided address.
     *
     * @param address the JMS queue address
     * @return the exercise id
     * @throws JMSException if the exercise id could not be extracted
     */
    public static Long extractExerciseIdFromAddress(String address) throws JMSException {
        var matcher = EXERCISE_ID_TOPIC_EXTRACTION_PATTERN.matcher(address);
        if (!matcher.matches()) {
            throw new JMSException("Could not extract exercise id");
        }

        return Long.parseLong(matcher.group(1));
    }

}
