package de.tum.in.www1.artemis.service.messaging.services;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.dto.UserExerciseDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Message consumer to receive and process messages related to the exercise service.
 */
@Component
@EnableJms
public class LectureServiceConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LectureServiceConsumer.class);

    @Autowired
    private final JmsTemplate jmsTemplate;

    private ExerciseService exerciseService;

    public LectureServiceConsumer(ExerciseService exerciseService, JmsTemplate jmsTemplate) {
        this.exerciseService = exerciseService;
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * Consume the message, filter the exercises and send response message
     *
     * @param message the received message
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES)
    public void filterExercisesAndRespond(Message message) {
        UserExerciseDTO userExerciseDTO;
        try {
            userExerciseDTO = message.getBody(UserExerciseDTO.class);
        }
        catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
        LOGGER.info("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES, userExerciseDTO);
        SecurityUtils.setAuthorizationObject();
        Set<Exercise> result;
        if (userExerciseDTO == null || userExerciseDTO.getUser() == null || CollectionUtils.isEmpty(userExerciseDTO.getExercises())) {
            result = Set.of();
        }
        else {
            Set<Exercise> exercisesUserIsAllowedToSee = exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(userExerciseDTO.getExercises()),
                    userExerciseDTO.getUser());
            result = exerciseService.loadExercisesWithInformationForDashboard(exercisesUserIsAllowedToSee.stream().map(Exercise::getId).collect(Collectors.toSet()),
                    userExerciseDTO.getUser());
        }
        LOGGER.info("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, result);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, result, msg -> {
            msg.setJMSCorrelationID(message.getJMSCorrelationID());
            return msg;
        });
    }
}
