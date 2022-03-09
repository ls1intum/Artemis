package de.tum.in.www1.artemis.service.messaging.services;

import java.util.HashSet;
import java.util.Set;

import javax.jms.JMSException;
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
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

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
        log.debug("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS, userLectureDTO);
        String correlationId = Integer.toString(userLectureDTO.hashCode());
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS, userLectureDTO, message -> {
            message.setJMSCorrelationID(correlationId);
            return message;
        });
        Message responseMessage = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, "JMSCorrelationID='" + correlationId + "'");
        Set<Lecture> filteredLectures;
        try {
            filteredLectures = responseMessage.getBody(Set.class);
            log.debug("Received response in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE, filteredLectures);
        }
        catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
        return filteredLectures;
    }

    /**
     * Send a message to the Lecture microservice to remove exercise unit from a lecture.
     *
     * @param exerciseId the id of the exercise to be removed
     * @return true or false whether the exercise unit has been successfully removed
     */
    public boolean removeExerciseUnits(long exerciseId) {
        log.debug("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, exerciseId);
        String correlationId = Long.toString(exerciseId);
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS, exerciseId, message -> {
            message.setJMSCorrelationID(correlationId);
            return message;
        });
        Message responseMessage = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, "JMSCorrelationID='" + correlationId + "'");
        boolean result;
        try {
            result = responseMessage.getBody(Boolean.class);
            log.debug("Received response in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE, result);
        }
        catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
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
        log.debug("Send message in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES, lectures);
        String correlationId = Integer.toString(lectures.hashCode());
        jmsTemplate.convertAndSend(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES, lectures, message -> {
            message.setJMSCorrelationID(correlationId);
            return message;
        });
        Message responseMessage = jmsTemplate.receiveSelected(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, "JMSCorrelationID='" + correlationId + "'");
        boolean result;
        try {
            result = responseMessage.getBody(Boolean.class);
            log.debug("Received response in queue {} with body {}", MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES_RESPONSE, result);
        }
        catch (JMSException e) {
            throw new InternalServerErrorException("There was a problem with the communication between server components. Please try again later!");
        }
        return result;
    }
}
