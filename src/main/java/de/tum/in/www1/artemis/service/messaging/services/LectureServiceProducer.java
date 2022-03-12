package de.tum.in.www1.artemis.service.messaging.services;

import java.util.HashSet;
import java.util.Set;

import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.UserLectureDTO;
import de.tum.in.www1.artemis.service.util.JmsMessageUtil;

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
     * Send a message to the Lecture microservice to filter the active attachments for a given user and wait for response.
     *
     * @param lectures the lectures
     * @param user     the user
     * @return the response from the Lecture microservice including set of lectures
     */
    public Set<Lecture> filterActiveAttachments(Set<Lecture> lectures, User user) {
        if (CollectionUtils.isEmpty(lectures) || user == null) {
            return new HashSet<>();
        }
        UserLectureDTO userLectureDTO = new UserLectureDTO(lectures, user);
        String correlationId = Integer.toString(userLectureDTO.hashCode());
        log.debug("Send message in queue {} with correlation id {} and body {}.", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS, correlationId, userLectureDTO);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS, userLectureDTO, JmsMessageUtil.withCorrelationId(correlationId));

        Message message = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, JmsMessageUtil.getJmsMessageSelector(correlationId));
        Set<Lecture> filteredLectures = JmsMessageUtil.parseBody(message, Set.class);
        log.debug("Received message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, correlationId,
                filteredLectures);

        return filteredLectures;
    }

    /**
     * Send a message to the Lecture microservice to remove exercise unit from a lecture.
     *
     * @param exerciseId the id of the exercise to be removed
     * @return true or false whether the exercise unit has been successfully removed
     */
    public boolean removeExerciseUnits(long exerciseId) {
        String correlationId = Long.toString(exerciseId);
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, correlationId, exerciseId);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, exerciseId, JmsMessageUtil.withCorrelationId(correlationId));

        Message message = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, JmsMessageUtil.getJmsMessageSelector(correlationId));
        boolean result = JmsMessageUtil.parseBody(message, Boolean.class);
        log.debug("Received message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, correlationId, result);

        return result;
    }

    /**
     * Send a message to the Lecture microservice to delete lectures.
     *
     * @param course the course which lectures should be deleted
     * @return true or false whether the exercise unit have been successfully deleted
     */
    public boolean deleteLecturesOfCourse(Course course) {
        if (course == null || CollectionUtils.isEmpty(course.getLectures())) {
            return true;
        }
        Set<Lecture> lectures = course.getLectures();
        String correlationId = Integer.toString(lectures.hashCode());
        log.debug("Send message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES, correlationId, lectures);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES, lectures, JmsMessageUtil.withCorrelationId(correlationId));

        Message message = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, JmsMessageUtil.getJmsMessageSelector(correlationId));
        boolean result = JmsMessageUtil.parseBody(message, Boolean.class);
        log.debug("Received message in queue {} with correlation id {} and body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, correlationId, result);

        return result;
    }
}
