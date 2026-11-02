package de.tum.cit.aet.artemis.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.notification.domain.course_notifications.AddedToChannelNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.AtlasCompetencyUpdateNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.AttachmentChangedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ChannelDeletedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.DeregisteredFromTutorialGroupNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.DuplicateTestCaseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ExerciseAssessedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ExerciseOpenForPracticeNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ExerciseUpdatedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.IrisResponseNeedsReviewNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.IrisResponseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewAnnouncementNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewAnswerNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewCpcPlagiarismCaseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewExerciseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewManualFeedbackRequestNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewMentionNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewPlagiarismCaseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewPostNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.PlagiarismCaseVerdictNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ProgrammingBuildRunUpdateNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ProgrammingTestCasesChangedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.QuizExerciseStartedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.RegisteredToTutorialGroupNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.RemovedFromChannelNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.TutorialGroupAssignedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.TutorialGroupDeletedNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.TutorialGroupUnassignedNotification;

class CourseNotificationDeliveryPolicyTest {

    @ParameterizedTest
    @MethodSource("notificationPolicies")
    void preservesDeliveryAndRetention(Class<? extends CourseNotification> type, CourseNotificationCategory category, int retentionDays, List<NotificationChannelOption> channels)
            throws Exception {
        CourseNotification notification = type.getConstructor(Long.class, Long.class, ZonedDateTime.class, Map.class).newInstance(1L, 2L, ZonedDateTime.now(), Map.of());
        assertThat(notification.getCourseNotificationCategory()).isEqualTo(category);
        assertThat(notification.getCleanupDuration()).isEqualTo(Duration.ofDays(retentionDays));
        assertThat(notification.getSupportedChannels()).containsExactlyElementsOf(channels);
    }

    private static Stream<Arguments> notificationPolicies() {
        return Stream.of(
                Arguments.of(QuizExerciseStartedNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(DuplicateTestCaseNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(ExerciseAssessedNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(TutorialGroupUnassignedNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewAnswerNotification.class, CourseNotificationCategory.COMMUNICATION, 7, List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(ExerciseOpenForPracticeNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewAnnouncementNotification.class, CourseNotificationCategory.COMMUNICATION, 30,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewManualFeedbackRequestNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(ChannelDeletedNotification.class, CourseNotificationCategory.COMMUNICATION, 7,
                        List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(ProgrammingBuildRunUpdateNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(PlagiarismCaseVerdictNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(DeregisteredFromTutorialGroupNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewPostNotification.class, CourseNotificationCategory.COMMUNICATION, 7, List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(IrisResponseNeedsReviewNotification.class, CourseNotificationCategory.IRIS_REVIEW, 7, List.of(NotificationChannelOption.WEBAPP)),
                Arguments.of(NewPlagiarismCaseNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(AtlasCompetencyUpdateNotification.class, CourseNotificationCategory.GENERAL, 14, List.of(NotificationChannelOption.EMAIL)),
                Arguments.of(AddedToChannelNotification.class, CourseNotificationCategory.COMMUNICATION, 7,
                        List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(ProgrammingTestCasesChangedNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(RegisteredToTutorialGroupNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(TutorialGroupAssignedNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(TutorialGroupDeletedNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(AttachmentChangedNotification.class, CourseNotificationCategory.GENERAL, 7, List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewExerciseNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(IrisResponseNotification.class, CourseNotificationCategory.GENERAL, 7, List.of(NotificationChannelOption.PUSH)),
                Arguments.of(ExerciseUpdatedNotification.class, CourseNotificationCategory.GENERAL, 14,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewCpcPlagiarismCaseNotification.class, CourseNotificationCategory.GENERAL, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(RemovedFromChannelNotification.class, CourseNotificationCategory.COMMUNICATION, 7,
                        List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)),
                Arguments.of(NewMentionNotification.class, CourseNotificationCategory.COMMUNICATION, 7,
                        List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH)));
    }
}
