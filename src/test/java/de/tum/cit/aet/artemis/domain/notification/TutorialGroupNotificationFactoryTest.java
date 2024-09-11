package de.tum.cit.aet.artemis.domain.notification;

import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.TUTORIAL_GROUP_DELETED_TEXT;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.TUTORIAL_GROUP_DELETED_TITLE;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.TUTORIAL_GROUP_UPDATED_TEXT;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.TUTORIAL_GROUP_UPDATED_TITLE;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createTutorialGroupTarget;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.domain.notification.NotificationTarget;
import de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotificationFactory;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;

class TutorialGroupNotificationFactoryTest {

    private static TutorialGroup tutorialGroup;

    private static final Long TUTORIAL_GROUP_ID = 21L;

    private static final Long COURSE_ID = 12L;

    private static final String TUTORIAL_GROUP_TITLE = "tutorial group title";

    @BeforeAll
    static void setUp() {
        Course course = new Course();
        course.setId(COURSE_ID);

        User teachingAssistant = new User();
        teachingAssistant.setFirstName("John");
        teachingAssistant.setLastName("Doe");

        tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setId(TUTORIAL_GROUP_ID);
        tutorialGroup.setTitle(TUTORIAL_GROUP_TITLE);
        tutorialGroup.setTeachingAssistant(teachingAssistant);

        User tutorialGroupStudent = new User();
        tutorialGroupStudent.setFirstName("Jane");
        tutorialGroupStudent.setLastName("Doe");

        User instructor = new User();
        instructor.setFirstName("John");
        instructor.setLastName("Smith");

    }

    @Test
    void createNotification_TutorialGroupDeleted() {
        var notificationFromFactory = TutorialGroupNotificationFactory.createTutorialGroupNotification(tutorialGroup, NotificationType.TUTORIAL_GROUP_DELETED);
        checkNotification(notificationFromFactory, tutorialGroup, TUTORIAL_GROUP_DELETED_TITLE, TUTORIAL_GROUP_DELETED_TEXT, true, "[null,\"" + TUTORIAL_GROUP_TITLE + "\"]",
                NotificationType.TUTORIAL_GROUP_DELETED, createTutorialGroupTarget(tutorialGroup, COURSE_ID, false, false));
    }

    @Test
    void createNotification_TutorialGroupUpdated() {
        var notificationFromFactory = TutorialGroupNotificationFactory.createTutorialGroupNotification(tutorialGroup, NotificationType.TUTORIAL_GROUP_UPDATED);
        checkNotification(notificationFromFactory, tutorialGroup, TUTORIAL_GROUP_UPDATED_TITLE, TUTORIAL_GROUP_UPDATED_TEXT, true, "[null,\"" + tutorialGroup.getTitle() + "\"]",
                NotificationType.TUTORIAL_GROUP_UPDATED, createTutorialGroupTarget(tutorialGroup, COURSE_ID, false, true));
    }

    private void checkNotification(TutorialGroupNotification tutorialGroupNotification, TutorialGroup expectedTutorialGroup, String expectedTitle, String expectedText,
            boolean expectedTextIsPlaceholder, String expectedPlaceholderValues, NotificationType expectedNotificationType, NotificationTarget expectedTarget) {
        assertThat(tutorialGroupNotification.getTutorialGroup()).isEqualTo(expectedTutorialGroup);
        assertThat(tutorialGroupNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(tutorialGroupNotification.getText()).isEqualTo(expectedText);
        assertThat(tutorialGroupNotification.getTextIsPlaceholder()).isEqualTo(expectedTextIsPlaceholder);
        assertThat(tutorialGroupNotification.getPlaceholderValues()).isEqualTo(expectedPlaceholderValues);
        assertThat(tutorialGroupNotification.notificationType).isEqualTo(expectedNotificationType);
        assertThat(tutorialGroupNotification.getTarget()).isEqualTo(expectedTarget.toJsonString());
    }

}
