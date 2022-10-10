package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.createTutorialGroupTarget;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.TUTORIAL_GROUP_DELETED_TITLE;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.TUTORIAL_GROUP_UPDATED_TITLE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

public class TutorialGroupNotificationFactoryTest {

    private static TutorialGroup tutorialGroup;

    private static final Long TUTORIAL_GROUP_ID = 21L;

    private static final Long COURSE_ID = 12L;

    private static final String TUTORIAL_GROUP_TITLE = "tutorial group title";

    private static Course course;

    private static User teachingAssistant;

    private static User instructor;

    private static User tutorialGroupStudent;

    @BeforeAll
    static void setUp() {
        course = new Course();
        course.setId(COURSE_ID);

        teachingAssistant = new User();
        teachingAssistant.setFirstName("John");
        teachingAssistant.setLastName("Doe");

        tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setId(TUTORIAL_GROUP_ID);
        tutorialGroup.setTitle(TUTORIAL_GROUP_TITLE);
        tutorialGroup.setTeachingAssistant(teachingAssistant);

        tutorialGroupStudent = new User();
        tutorialGroupStudent.setFirstName("Jane");
        tutorialGroupStudent.setLastName("Doe");

        instructor = new User();
        instructor.setFirstName("John");
        instructor.setLastName("Smith");

    }

    @Test
    void createNotification_TutorialGroupDeleted() {
        var notificationFromFactory = TutorialGroupNotificationFactory.createTutorialGroupNotification(tutorialGroup, NotificationType.TUTORIAL_GROUP_DELETED);
        checkNotification(notificationFromFactory, tutorialGroup, TUTORIAL_GROUP_DELETED_TITLE, "The tutorial group " + TUTORIAL_GROUP_TITLE + " has been deleted.",
                NotificationType.TUTORIAL_GROUP_DELETED, createTutorialGroupTarget(tutorialGroup, COURSE_ID, false, false));
    }

    @Test
    void createNotification_TutorialGroupUpdated() {
        var notificationFromFactory = TutorialGroupNotificationFactory.createTutorialGroupNotification(tutorialGroup, NotificationType.TUTORIAL_GROUP_UPDATED);
        checkNotification(notificationFromFactory, tutorialGroup, TUTORIAL_GROUP_UPDATED_TITLE, "The tutorial group " + tutorialGroup.getTitle() + " has been updated.",
                NotificationType.TUTORIAL_GROUP_UPDATED, createTutorialGroupTarget(tutorialGroup, COURSE_ID, false, true));
    }

    private void checkNotification(TutorialGroupNotification tutorialGroupNotification, TutorialGroup expectedTutorialGroup, String expectedTitle, String expectedText,
            NotificationType expectedNotificationType, NotificationTarget expectedTarget) {
        assertThat(tutorialGroupNotification.getTutorialGroup()).isEqualTo(expectedTutorialGroup);
        assertThat(tutorialGroupNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(tutorialGroupNotification.getText()).isEqualTo(expectedText);
        assertThat(tutorialGroupNotification.notificationType).isEqualTo(expectedNotificationType);
        assertThat(tutorialGroupNotification.getTarget()).isEqualTo(expectedTarget.toJsonString());
    }

}
