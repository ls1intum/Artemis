package de.tum.in.www1.artemis.web.websocket.jms;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
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

    private static final String QUEUE_PREFIX = "queue://";

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
     * Uses a wildcard address that will listen to all submissions - only supported with an actual ActiveMQ broker.
     * For testing, use @see {@link QuizJMSListenerService#quizSubmissionMessageListener(ConnectionFactory, String)}
     * to specify a destination for testing.
     *
     * @param connectionFactory the connection factory provided by Spring
     * @return the SimpleMessageListenerContainer that will be used by Spring to register the topic
     */
    @Bean
    public SimpleMessageListenerContainer quizSubmissionMessageListener(final ConnectionFactory connectionFactory) {
        return quizSubmissionMessageListener(connectionFactory, "/queue/quizExercise/*/submission");
    }

    /**
     * Create a SimpleMessageListenerContainer that uses the specified connectionFactory to listen on the specified
     * destination and forward messages to the MessageListenerAdapter.
     *
     * @param connectionFactory the ConnectionFactory that should be used for the consumers
     * @param destination       the destination on which the consumers should listen
     * @return a SimpleMessageListenerContainer that forwards received messages to the MessageListenerAdapter
     */
    public SimpleMessageListenerContainer quizSubmissionMessageListener(final ConnectionFactory connectionFactory, String destination) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        // Set the name of the queue that should be processed by the message listener
        container.setDestinationName(destination);
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
            public void onMessage(@NotNull Message message, Session session) throws JMSException {
                // Message is of type ActiveMQMessage because we use Apache ActiveMQ as broker
                BytesMessage bytesMessage = (BytesMessage) message;

                var messageLength = bytesMessage.getBodyLength();
                byte[] bytes = new byte[(int) messageLength];
                // Read message into bytes[]-array
                bytesMessage.readBytes(bytes);

                try {
                    var quizSubmission = objectMapper.readValue(bytes, QuizSubmission.class);
                    var address = extractDestinationAddressFromMessage(message);

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
                    logger.warn(String.format("Could not read JMS message: %s", e));
                }
            }
        });
    }

    private static String extractDestinationAddressFromMessage(Message message) throws JMSException {
        var destination = message.getJMSDestination();
        if (destination instanceof ActiveMQDestination activeMQDestination) {
            return activeMQDestination.getAddress();
        }

        return destination.toString();
    }

    /**
     * Extract the exercise id from the provided address.
     *
     * @param address the JMS queue address
     * @return the exercise id
     * @throws JMSException if the exercise id could not be extracted
     */
    public static Long extractExerciseIdFromAddress(String address) throws JMSException {
        var addressWithoutPrefix = address;
        if (address.startsWith(QUEUE_PREFIX)) {
            addressWithoutPrefix = address.replaceAll(QUEUE_PREFIX, "");
        }

        var matcher = EXERCISE_ID_TOPIC_EXTRACTION_PATTERN.matcher(addressWithoutPrefix);
        if (!matcher.matches()) {
            throw new JMSException("Could not extract exercise id");
        }

        return Long.parseLong(matcher.group(1));
    }

}
