package de.tum.cit.aet.artemis.communication.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.AttachmentChangedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.DuplicateTestCaseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ExerciseAssessedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ExerciseOpenForPracticeNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ExerciseUpdatedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewAnnouncementNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewAnswerNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewCpcPlagiarismCaseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewExerciseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewManualFeedbackRequestNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewMentionNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPlagiarismCaseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPostNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.PlagiarismCaseVerdictNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ProgrammingBuildRunUpdateNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ProgrammingTestCasesChangedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.QuizExerciseStartedNotification;

@CourseNotificationSettingPreset(2)
public class AllActivityUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public AllActivityUserCourseNotificationSettingPreset() {
        presetMap = Map.ofEntries(
                Map.entry(NewPostNotification.class, Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewAnswerNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewMentionNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewAnnouncementNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewExerciseNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(ExerciseOpenForPracticeNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(ExerciseAssessedNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(ExerciseUpdatedNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(QuizExerciseStartedNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(AttachmentChangedNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewManualFeedbackRequestNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(DuplicateTestCaseNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewCpcPlagiarismCaseNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(NewPlagiarismCaseNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(ProgrammingBuildRunUpdateNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(ProgrammingTestCasesChangedNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)),
                Map.entry(PlagiarismCaseVerdictNotification.class,
                        Map.of(NotificationChannelOption.EMAIL, true, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true)));
    }
}
