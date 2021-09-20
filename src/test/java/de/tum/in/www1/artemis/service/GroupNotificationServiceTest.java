package de.tum.in.www1.artemis.service;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

public class GroupNotificationServiceTest {

    @Autowired
    private static GroupNotificationService groupNotificationService;

    @Autowired
    private static GroupNotificationRepository groupNotificationRepository;

    @Mock
    private static UserRepository userRepository;

    @Mock
    private static SimpMessageSendingOperations messageTemplate;

    @Mock
    private static User author;

    @Mock
    private static Exercise exercise;

    @Mock
    private static Course course;

    private static final Long courseId = 42L;

    @Mock
    private static GroupNotification notification;

    /**
     * Prepares the testing suite by initializing variables and mocks
     */
    @BeforeAll
    public static void setUp() {
        author = mock(User.class);
        notification = mock(GroupNotification.class);
        course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);
        exercise = mock(Exercise.class);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        mockStatic(GroupNotificationFactory.class);
        when(GroupNotificationFactory.createNotification(exercise, author, GroupNotificationType.STUDENT, NotificationType.EXERCISE_CREATED, null)).thenReturn(notification);

        groupNotificationRepository = mock(GroupNotificationRepository.class);
        messageTemplate = mock(SimpMessageSendingOperations.class);
        userRepository = mock(UserRepository.class);
        when(userRepository.getUser()).thenReturn(author);
        groupNotificationService = new GroupNotificationService(groupNotificationRepository, messageTemplate, userRepository);
    }

    /**
     * (Exercise Created) Notifications should be saved/send only at their release date.
     */
    @Test
    public void testPrepareNotificationForStudentAndTutorGroupAboutStartedExercise() {
        ZonedDateTime releaseDate = ZonedDateTime.now().plusSeconds(1);
        when(exercise.getReleaseDate()).thenReturn(releaseDate);

        groupNotificationService.prepareNotificationForStudentAndTutorGroupAboutStartedExercise(exercise);

        verify(groupNotificationRepository, times(0)).save(any());
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        verify(groupNotificationRepository, times(0)).save(any());
        try {
            Thread.sleep(600);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        // (more) than one second later (after release date) the notifications should have been send/saved
        verify(groupNotificationRepository, times(2)).save(any());
    }
}
