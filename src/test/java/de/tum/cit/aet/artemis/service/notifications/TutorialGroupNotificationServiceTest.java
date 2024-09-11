package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_DELETED_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_UPDATED_TITLE;
import static de.tum.cit.aet.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.course.CourseUtilService;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.NotificationSetting;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.Language;
import de.tum.cit.aet.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.domain.notification.Notification;
import de.tum.cit.aet.artemis.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupNotificationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.user.UserUtilService;

class TutorialGroupNotificationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutorialgroupnotifservice";

    private static final int STUDENT_COUNT = 5;

    private static final int TUTOR_COUNT = 1;

    @Autowired
    private TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    @Autowired
    private TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    private TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private TutorialGroup tutorialGroup;

    private User tutor1;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, STUDENT_COUNT, TUTOR_COUNT, 0, 1);
        Course course = courseUtilService.createCourse();
        userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        tutorialGroup = createAndSaveTutorialGroup(course.getId(), "title" + course.getId(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH,
                userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow(), IntStream.range(1, STUDENT_COUNT + 1)
                        .mapToObj((studentId) -> userRepository.findOneByLogin(TEST_PREFIX + "student" + studentId).orElseThrow()).collect(Collectors.toSet()));

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        tutorialGroupNotificationRepository.deleteAll();
        notificationSettingRepository.deleteAll();
    }

    @Override
    @AfterEach
    protected void resetSpyBeans() {
        super.resetSpyBeans();
    }

    private void verifyRepositoryCallWithCorrectNotification(int numberOfGroupsAndCalls, String expectedNotificationTitle) {
        List<TutorialGroupNotification> capturedNotifications = tutorialGroupNotificationRepository.findAll();
        Notification capturedNotification = capturedNotifications.getFirst();
        assertThat(capturedNotification.getTitle()).isEqualTo(expectedNotificationTitle);
        assertThat(capturedNotifications).hasSize(numberOfGroupsAndCalls);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void notifyAboutTutorialGroupUpdate_shouldSaveAndSend(boolean contactTutor) {
        List<Long> studentSettings = IntStream.range(1, STUDENT_COUNT + 1).mapToLong((studentId) -> {
            var student = userRepository.findOneByLogin(TEST_PREFIX + "student" + studentId).orElseThrow();
            return prepareNotificationSettingForTest(student, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE).getId();
        }).boxed().toList();

        var setting2 = prepareNotificationSettingForTest(tutor1, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE);

        tutorialGroupNotificationService.notifyAboutTutorialGroupUpdate(tutorialGroup, contactTutor, "LoremIpsum");
        verifyRepositoryCallWithCorrectNotification(1, TUTORIAL_GROUP_UPDATED_TITLE);
        if (contactTutor) {
            verifyEmail(STUDENT_COUNT + 1);
        }
        else {
            verifyEmail(STUDENT_COUNT);
        }

        studentSettings.forEach((settingId) -> notificationSettingRepository.deleteById(settingId));

        notificationSettingRepository.deleteById(setting2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void notifyAboutTutorialGroupDeletion_shouldSaveAndSend() {
        List<Long> studentSettings = IntStream.range(1, STUDENT_COUNT + 1).mapToLong((studentId) -> {
            var student = userRepository.findOneByLogin(TEST_PREFIX + "student" + studentId).orElseThrow();
            return prepareNotificationSettingForTest(student, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE).getId();
        }).boxed().toList();

        var settingTutor = prepareNotificationSettingForTest(tutor1, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE);
        tutorialGroupNotificationService.notifyAboutTutorialGroupDeletion(tutorialGroup);
        verifyRepositoryCallWithCorrectNotification(1, TUTORIAL_GROUP_DELETED_TITLE);
        verifyEmail(STUDENT_COUNT + 1);

        studentSettings.forEach((settingId) -> notificationSettingRepository.deleteById(settingId));
        notificationSettingRepository.deleteById(settingTutor.getId());
    }

    private NotificationSetting prepareNotificationSettingForTest(User user, String notificationSettingIdentifier) {
        NotificationSetting notificationSetting = new NotificationSetting(user, true, true, true, notificationSettingIdentifier);
        notificationSettingRepository.save(notificationSetting);
        return notificationSetting;
    }

    private void verifyEmail(int times) {
        verify(javaMailSender, timeout(2500).times(times)).createMimeMessage();
    }

    private TutorialGroup createAndSaveTutorialGroup(Long courseId, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus,
            Language language, User teachingAssistant, Set<User> registeredStudents) {
        var course = courseRepository.findByIdElseThrow(courseId);

        var tutorialGroup = tutorialGroupRepository
                .saveAndFlush(new TutorialGroup(course, title, additionalInformation, capacity, isOnline, campus, language.name(), teachingAssistant, new HashSet<>()));

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : registeredStudents) {
            registrations.add(new TutorialGroupRegistration(student, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);

        return tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroup.getId());
    }

}
