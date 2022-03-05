package de.tum.in.www1.artemis.lecture.service.messaging;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.service.dto.UserLectureDTO;
import de.tum.in.www1.artemis.lecture.service.LectureService;
import de.tum.in.www1.artemis.lecture.service.LectureUnitService;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Set;

/**
 * Message broker consumer to consume messages coming from Artemis
 */
@Component
@EnableJms
public class ArtemisConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisConsumer.class);

    @Autowired
    private final JmsTemplate jmsTemplate;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private LectureService lectureService;

    public ArtemisConsumer(JmsTemplate jmsTemplate,LectureUnitService lectureUnitService,LectureService lectureService) {
        this.jmsTemplate = jmsTemplate;
        this.lectureUnitService = lectureUnitService;
        this.lectureService  = lectureService;
    }

    /**
     * Consume message, filter active lecture attachments and send response
     *
     * @param message the message to consume
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS)
    public void filterLectureActiveAttachments(Message message) {
        UserLectureDTO userLectureDTO;
        try {
            userLectureDTO = message.getBody(UserLectureDTO.class);
        } catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
        LOGGER.info("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS, userLectureDTO);
        SecurityUtils.setAuthorizationObject();
        Set<Lecture> lectures = lectureService.filterActiveAttachments(userLectureDTO.getLectures(), userLectureDTO.getUser());
        LOGGER.info("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, lectures);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, lectures, msg -> {
            msg.setJMSCorrelationID(message.getJMSCorrelationID());
            return msg;
        });
    }

    /**
     * Consume message, remove exercise units and send response
     *
     * @param message the message to consume
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS)
    public void sendRemoveExerciseUnitsResponse(Message message) {
        LOGGER.info("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, message.toString());

        Long exerciseId;
        try {
            LOGGER.info("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, message);
            exerciseId = message.getBody(Long.class);
        } catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
        LOGGER.info("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, exerciseId);
        SecurityUtils.setAuthorizationObject();
        lectureUnitService.removeExerciseUnitsByExerciseId(exerciseId);
        LOGGER.info("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, true);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, true, msg -> {
            msg.setJMSCorrelationID(message.getJMSCorrelationID());
            return msg;
        });
    }

    /**
     * Consume message, delete lectures and send response
     *
     * @param message the message to consume
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES)
    public void deleteLectures(Message message) {
        Set<Lecture> lectures;
        try {
            lectures = message.getBody(Set.class);
        } catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
        LOGGER.info("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES, lectures);
        SecurityUtils.setAuthorizationObject();
        for (Lecture lecture : lectures) {
            lectureService.delete(lecture);
        }

        LOGGER.info("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, true);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, true, msg -> {
            msg.setJMSCorrelationID(message.getJMSCorrelationID());
            return msg;
        });
    }
}
