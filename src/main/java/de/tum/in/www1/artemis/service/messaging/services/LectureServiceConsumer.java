package de.tum.in.www1.artemis.service.messaging.services;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import de.tum.in.www1.artemis.service.util.JmsMessageUtil;

/**
 * Message consumer to receive and process messages related to the exercise service.
 */
@Component
@EnableJms
public class LectureServiceConsumer {

    private final Logger log = LoggerFactory.getLogger(LectureServiceConsumer.class);

    private final JmsTemplate jmsTemplate;

    private final ExerciseService exerciseService;

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
        log.debug("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES, message.toString());
        UserExerciseDTO userExerciseDTO = JmsMessageUtil.parseBody(message, UserExerciseDTO.class);
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

        String correlationId = JmsMessageUtil.getCorrelationId(message);
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, correlationId, result);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE, result, JmsMessageUtil.withCorrelationId(correlationId));
    }
}
