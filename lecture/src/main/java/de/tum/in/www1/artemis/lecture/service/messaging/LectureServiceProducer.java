package de.tum.in.www1.artemis.lecture.service.messaging;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.UserExerciseDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
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
        log.debug("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES, userExerciseDTO);
        String correlationId = Integer.toString(userExerciseDTO.hashCode());
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES, userExerciseDTO, message -> {
            message.setJMSCorrelationID(correlationId);
            return message;
        });
        Message responseMessage = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, "JMSCorrelationID='" + correlationId + "'");
        log.debug("Received response in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, userExerciseDTO);
        try {
            return responseMessage.getBody(Set.class);
        } catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
    }
}
