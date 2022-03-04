package de.tum.in.www1.artemis.service.messaging.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;

import de.tum.in.www1.artemis.AbstractSpringDevelopmentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

public class LectureServiceProducerTest extends AbstractSpringDevelopmentTest {

    @Mock
    private Message message;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private LectureServiceProducer lectureServiceProducer;

    private ArgumentCaptor<Set<String>> setCaptor;

    private User user;

    private Set<Lecture> lectures;

    private Course course;

    @BeforeEach
    public void setUp() throws Exception {
        this.message = Mockito.mock(Message.class);
        setCaptor = ArgumentCaptor.forClass(Set.class);

        user = new User();
        user.setLogin("student");

        Lecture lecture = new Lecture();
        lecture.setId(1L);
        lectures = Set.of(lecture);

        course = new Course();
        course.setId(1L);
        course.setLectures(lectures);
    }

    @AfterEach
    public void resetDatabase() {
        Mockito.reset(message);
    }

    @Test
    public void testFilterActiveAttachments_nullLectures_shouldReturnEmptyList() {
        Set<Lecture> result = lectureServiceProducer.filterActiveAttachments(null, user);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testFilterActiveAttachments_nullInput_shouldReturnEmptyList() {
        Set<Lecture> result = lectureServiceProducer.filterActiveAttachments(null, null);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testFilterActiveAttachments_emptyLectures_shouldReturnEmptyList() {
        Set<Lecture> result = lectureServiceProducer.filterActiveAttachments(new HashSet<>(), new User());
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testFilterActiveAttachments_nullUser_shouldReturnEmptyList() {
        Set<Lecture> result = lectureServiceProducer.filterActiveAttachments(lectures, null);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testFilterActiveAttachments_shouldThrowException() throws JMSException {
        when(jmsTemplate.receiveSelected(anyString(), any())).thenReturn(message);
        when(message.getBody(Set.class)).thenThrow(new JMSException("Error"));

        assertThrows(InternalServerErrorException.class, () -> lectureServiceProducer.filterActiveAttachments(lectures, user));
    }

    @Test
    public void testFilterActiveAttachments_shouldReturnResponse() throws JMSException {
        when(jmsTemplate.receiveSelected(anyString(), any())).thenReturn(message);
        when(message.getBody(Set.class)).thenReturn(lectures);

        Set<Lecture> result = lectureServiceProducer.filterActiveAttachments(lectures, user);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(lectures);
    }

    @Test
    public void testRemoveExerciseUnits_shouldThrowException() throws JMSException {
        when(jmsTemplate.receiveSelected(anyString(), any())).thenReturn(message);
        when(message.getBody(Boolean.class)).thenThrow(new JMSException("Error"));

        assertThrows(InternalServerErrorException.class, () -> lectureServiceProducer.removeExerciseUnits(1L));
    }

    @Test
    public void testRemoveExerciseUnits_shouldReturnResponse() throws JMSException {
        when(jmsTemplate.receiveSelected(anyString(), any())).thenReturn(message);
        when(message.getBody(Boolean.class)).thenReturn(true);

        Boolean result = lectureServiceProducer.removeExerciseUnits(1L);
        assertThat(result).isTrue();
    }

    @Test
    public void testDeleteLecturesOfCourse_nullCourse_shouldReturnTrue() {
        Boolean result = lectureServiceProducer.deleteLecturesOfCourse(null);
        assertThat(result).isTrue();
    }

    @Test
    public void testDeleteLecturesOfCourse_emptyCourse_shouldReturnTrue() {
        Boolean result = lectureServiceProducer.deleteLecturesOfCourse(new Course());
        assertThat(result).isTrue();
    }

    @Test
    public void testDeleteLecturesOfCourse_shouldThrowException() throws JMSException {
        when(jmsTemplate.receiveSelected(anyString(), any())).thenReturn(message);
        when(message.getBody(Boolean.class)).thenThrow(new JMSException("Error"));

        assertThrows(InternalServerErrorException.class, () -> lectureServiceProducer.deleteLecturesOfCourse(course));
    }

    @Test
    public void testDeleteLecturesOfCourse_shouldReturnResponse() throws JMSException {
        when(jmsTemplate.receiveSelected(anyString(), any())).thenReturn(message);
        when(message.getBody(Boolean.class)).thenReturn(true);

        Boolean result = lectureServiceProducer.deleteLecturesOfCourse(course);
        assertThat(result).isTrue();
    }
}
