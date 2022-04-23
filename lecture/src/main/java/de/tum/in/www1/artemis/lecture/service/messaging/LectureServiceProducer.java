package de.tum.in.www1.artemis.lecture.service.messaging;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.UserExerciseDTO;
import de.tum.in.www1.artemis.service.util.JmsMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import java.util.Set;

/**
 * Message broker producer to send messages related to the exercise service
 */
@Component
@EnableJms
public class LectureServiceProducer {
    private final Logger log = LoggerFactory.getLogger(LectureServiceProducer.class);

    private final JmsTemplate jmsTemplate;

    public LectureServiceProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * Send a message to Artemis get lecture exercises accessible for the given user and wait for response
     *
     * @param exercises the exercises
     * @param user      the user
     * @return the response from Artemis including set of exercises
     */
    public Set<Exercise> getLectureExercises(Set<Exercise> exercises, User user) {
        UserExerciseDTO userExerciseDTO = new UserExerciseDTO(exercises, user);
        String correlationId = Integer.toString(userExerciseDTO.hashCode());
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES, correlationId, userExerciseDTO);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES, userExerciseDTO, JmsMessageUtil.withCorrelationId(correlationId));

        Message message = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, JmsMessageUtil.getJmsMessageSelector(correlationId));
        Set<Exercise> result = JmsMessageUtil.parseBody(message, Set.class);
        log.debug("Received message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, correlationId, result);

        return result;
    }
}
