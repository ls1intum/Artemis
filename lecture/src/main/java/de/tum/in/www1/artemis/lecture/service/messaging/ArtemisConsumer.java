package de.tum.in.www1.artemis.lecture.service.messaging;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.lecture.service.LectureService;
import de.tum.in.www1.artemis.lecture.service.LectureUnitService;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.UserLectureDTO;
import de.tum.in.www1.artemis.service.util.JmsMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import java.util.Set;

/**
 * Message broker consumer to consume messages coming from Artemis
 */
@Component
@EnableJms
public class ArtemisConsumer {
    private final Logger log = LoggerFactory.getLogger(ArtemisConsumer.class);

    private final JmsTemplate jmsTemplate;

    private final LectureUnitService lectureUnitService;

    private final LectureService lectureService;

    public ArtemisConsumer(JmsTemplate jmsTemplate, LectureUnitService lectureUnitService, LectureService lectureService) {
        this.jmsTemplate = jmsTemplate;
        this.lectureUnitService = lectureUnitService;
        this.lectureService = lectureService;
    }

    /**
     * Consume message, filter active lecture attachments and send response
     *
     * @param message the message to consume
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS)
    public void filterLectureActiveAttachments(Message message) {
        log.debug("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS, message.toString());
        UserLectureDTO userLectureDTO = JmsMessageUtil.parseBody(message, UserLectureDTO.class);
        SecurityUtils.setAuthorizationObject();
        Set<Lecture> lectures = lectureService.filterActiveAttachments(userLectureDTO.getLectures(), userLectureDTO.getUser());
        String correlationId = JmsMessageUtil.getCorrelationId(message);
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, correlationId, lectures);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, lectures, JmsMessageUtil.withCorrelationId(correlationId));
    }

    /**
     * Consume message, remove exercise units and send response
     *
     * @param message the message to consume
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS)
    public void sendRemoveExerciseUnitsResponse(Message message) {
        log.debug("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, message.toString());
        Long exerciseId = JmsMessageUtil.parseBody(message, Long.class);
        SecurityUtils.setAuthorizationObject();
        lectureUnitService.removeExerciseUnitsByExerciseId(exerciseId);
        String correlationId = JmsMessageUtil.getCorrelationId(message);
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, correlationId, true);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, true, JmsMessageUtil.withCorrelationId(correlationId));
    }

    /**
     * Consume message, delete lectures and send response
     *
     * @param message the message to consume
     */
    @JmsListener(destination = MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES)
    public void deleteLectures(Message message) {
        log.debug("Received message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES, message.toString());
        Set<Lecture> lectures = JmsMessageUtil.parseBody(message, Set.class);
        SecurityUtils.setAuthorizationObject();
        for (Lecture lecture : lectures) {
            lectureService.delete(lecture);
        }
        String correlationId = JmsMessageUtil.getCorrelationId(message);
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, correlationId, true);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, true, JmsMessageUtil.withCorrelationId(correlationId));
    }
}
