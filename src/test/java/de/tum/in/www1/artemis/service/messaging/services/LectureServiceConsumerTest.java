package de.tum.in.www1.artemis.service.messaging.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.UserExerciseDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

public class LectureServiceConsumerTest extends AbstractSpringDevelopmentTest {

    @Mock
    private Message message;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private LectureServiceConsumer lectureServiceConsumer;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    private ArgumentCaptor<Set<String>> setCaptor;

    private Set<Exercise> exercises;

    private User user1;

    private User user2;

    @BeforeEach
    public void setUp() throws Exception {
        this.message = Mockito.mock(Message.class);
        setCaptor = ArgumentCaptor.forClass(Set.class);

        SecurityUtils.setAuthorizationObject();
        this.database.addUsers(10, 10, 0, 10);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(true);
        Course course = this.courseRepository.findByIdWithExercisesAndLecturesElseThrow(courses.get(0).getId());
        Lecture lecture = course.getLectures().stream().findFirst().get();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst().get();
        // Add user that is not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));

        // Setting up a lecture with various kinds of content
        ExerciseUnit exerciseUnit = database.createExerciseUnit(textExercise);
        AttachmentUnit attachmentUnit = database.createAttachmentUnit();
        VideoUnit videoUnit = database.createVideoUnit();
        TextUnit textUnit = database.createTextUnit();

        lecture = database.addLectureUnitsToLecture(lecture, Set.of(exerciseUnit, attachmentUnit, videoUnit, textUnit));
        this.user1 = userRepository.findUserWithGroupsAndAuthoritiesByLogin("student1").get();
        this.user2 = userRepository.findUserWithGroupsAndAuthoritiesByLogin("student42").get();
        this.exercises = lecture.getLectureUnits().stream().filter(unit -> unit instanceof ExerciseUnit).map(unit -> ((ExerciseUnit) unit).getExercise())
                .collect(Collectors.toSet());
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void testGetExercisesAndRespond() throws JMSException {
        UserExerciseDTO dto = new UserExerciseDTO(exercises, user1);
        when(message.getBody(UserExerciseDTO.class)).thenReturn(dto);
        when(message.getJMSCorrelationID()).thenReturn(Integer.toString(dto.hashCode()));

        lectureServiceConsumer.getExercisesAndRespond(message);

        Mockito.verify(jmsTemplate, atLeastOnce()).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE), setCaptor.capture(), any());
        assertThat(setCaptor.getValue()).isEqualTo(exercises);
    }

    @Test
    public void testGetExercisesAndRespond_shouldResponseWithEmptyList() throws JMSException {
        UserExerciseDTO dto = new UserExerciseDTO(exercises, user2);
        when(message.getBody(UserExerciseDTO.class)).thenReturn(dto);
        when(message.getJMSCorrelationID()).thenReturn(Integer.toString(dto.hashCode()));

        lectureServiceConsumer.getExercisesAndRespond(message);

        Mockito.verify(jmsTemplate, atLeastOnce()).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE), setCaptor.capture(), any());
        assertThat(setCaptor.getValue().size()).isEqualTo(0);
    }

    @Test
    public void testGetExercisesAndRespond_emptyDTO_shouldResponseWithEmptyList() throws JMSException {
        UserExerciseDTO dto = new UserExerciseDTO();
        when(message.getBody(UserExerciseDTO.class)).thenReturn(dto);
        when(message.getJMSCorrelationID()).thenReturn(Integer.toString(dto.hashCode()));

        lectureServiceConsumer.getExercisesAndRespond(message);

        Mockito.verify(jmsTemplate, atLeastOnce()).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE), setCaptor.capture(), any());
        assertThat(setCaptor.getValue().size()).isEqualTo(0);
    }

    @Test
    public void testGetExercisesAndRespond_onlyUserSet_shouldResponseWithEmptyList() throws JMSException {
        UserExerciseDTO dto = new UserExerciseDTO();
        dto.setUser(user1);
        when(message.getBody(UserExerciseDTO.class)).thenReturn(dto);
        when(message.getJMSCorrelationID()).thenReturn(Integer.toString(dto.hashCode()));

        lectureServiceConsumer.getExercisesAndRespond(message);

        Mockito.verify(jmsTemplate, atLeastOnce()).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE), setCaptor.capture(), any());
        assertThat(setCaptor.getValue().size()).isEqualTo(0);
    }

    @Test
    public void testGetExercisesAndRespond_onlyExercisesSet_shouldResponseWithEmptyList() throws JMSException {
        UserExerciseDTO dto = new UserExerciseDTO();
        dto.setExercises(exercises);
        when(message.getBody(UserExerciseDTO.class)).thenReturn(dto);
        when(message.getJMSCorrelationID()).thenReturn(Integer.toString(dto.hashCode()));

        lectureServiceConsumer.getExercisesAndRespond(message);

        Mockito.verify(jmsTemplate, atLeastOnce()).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE), setCaptor.capture(), any());
        assertThat(setCaptor.getValue().size()).isEqualTo(0);
    }

    @Test
    public void testGetExercisesAndRespond_shouldThrowException() throws JMSException {
        when(message.getBody(UserExerciseDTO.class)).thenThrow(new JMSException("Error"));

        assertThrows(InternalServerErrorException.class, () -> lectureServiceConsumer.getExercisesAndRespond(message));
        Mockito.verify(jmsTemplate, times(0)).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_GET_EXERCISES_RESPONSE), any(), any());
    }
}
