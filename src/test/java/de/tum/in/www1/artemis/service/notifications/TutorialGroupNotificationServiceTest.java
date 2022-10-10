package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupNotificationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;

class TutorialGroupNotificationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    private TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    private TutorialGroup tutorialGroup;

    private User student1;

    private User tutor1;

    @BeforeEach
    void setUp() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);
        Course course = this.database.createCourse();
        student1 = userRepository.findOneByLogin("student1").get();
        tutor1 = userRepository.findOneByLogin("tutor1").get();
        tutorialGroup = createAndSaveTutorialGroup(course.getId(), "ExampleTitle1", "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH,
                userRepository.findOneByLogin("tutor1").get(), ImmutableSet.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get(),
                        userRepository.findOneByLogin("student3").get(), userRepository.findOneByLogin("student4").get(), userRepository.findOneByLogin("student5").get()));

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    private void verifyRepositoryCallWithCorrectNotification(int numberOfGroupsAndCalls, String expectedNotificationTitle) {
        List<Notification> capturedNotifications = tutorialGroupNotificationRepository.findAll();
        Notification capturedNotification = capturedNotifications.get(0);
        assertThat(capturedNotification.getTitle()).isEqualTo(expectedNotificationTitle);
        assertThat(capturedNotifications).hasSize(numberOfGroupsAndCalls);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void notifyAboutTutorialGroupUpdate_shouldSaveAndSend(boolean contactTutor) {
        prepareNotificationSettingForTest(student1, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE);
        prepareNotificationSettingForTest(tutor1, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE);

        tutorialGroupNotificationService.notifyAboutTutorialGroupUpdate(tutorialGroup, contactTutor);
        verifyRepositoryCallWithCorrectNotification(1, TUTORIAL_GROUP_UPDATED_TITLE);
        if (contactTutor) {
            verifyEmail(2);
        }
        else {
            verifyEmail(1);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void notifyAboutTutorialGroupDeletion_shouldSaveAndSend() {
        prepareNotificationSettingForTest(student1, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE);
        tutorialGroupNotificationService.notifyAboutTutorialGroupDeletion(tutorialGroup);
        verifyRepositoryCallWithCorrectNotification(1, TUTORIAL_GROUP_DELETED_TITLE);
        verifyEmail(1);
    }

    private void prepareNotificationSettingForTest(User user, String notificationSettingIdentifier) {
        NotificationSetting notificationSetting = new NotificationSetting(user, true, true, notificationSettingIdentifier);
        notificationSettingRepository.save(notificationSetting);
    }

    private void verifyEmail(int times) {
        verify(javaMailSender, timeout(1500).times(times)).createMimeMessage();
    }

    private TutorialGroup createAndSaveTutorialGroup(Long courseId, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus,
            Language language, User teachingAssistant, Set<User> registeredStudents) {
        var course = courseRepository.findByIdElseThrow(courseId);

        var tutorialGroup = tutorialGroupRepository
                .saveAndFlush(new TutorialGroup(course, title, additionalInformation, capacity, isOnline, campus, language, teachingAssistant, new HashSet<>()));

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : registeredStudents) {
            registrations.add(new TutorialGroupRegistration(student, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);

        return tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroup.getId());
    }

}
