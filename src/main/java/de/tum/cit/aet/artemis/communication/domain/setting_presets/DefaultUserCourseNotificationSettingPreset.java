package de.tum.cit.aet.artemis.communication.domain.setting_presets;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.AddedToChannelNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.AttachmentChangedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ChannelDeletedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.DeregisteredFromTutorialGroupNotification;
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
import de.tum.cit.aet.artemis.communication.domain.course_notifications.RegisteredToTutorialGroupNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.RemovedFromChannelNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupAssignedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupDeletedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupUnassignedNotification;

@CourseNotificationSettingPreset(1)
public class DefaultUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public DefaultUserCourseNotificationSettingPreset() {
        presetMap = new HashMap<>();

        presetMap.put(NewPostNotification.class, createChannels(false, false, true));
        presetMap.put(NewAnswerNotification.class, createChannels(false, true, true));
        presetMap.put(NewMentionNotification.class, createChannels(false, true, true));
        presetMap.put(NewAnnouncementNotification.class, createChannels(true, true, true));
        presetMap.put(NewExerciseNotification.class, createChannels(false, true, true));
        presetMap.put(ExerciseOpenForPracticeNotification.class, createChannels(false, true, true));
        presetMap.put(ExerciseAssessedNotification.class, createChannels(true, true, true));
        presetMap.put(ExerciseUpdatedNotification.class, createChannels(false, true, true));
        presetMap.put(QuizExerciseStartedNotification.class, createChannels(false, true, true));
        presetMap.put(AttachmentChangedNotification.class, createChannels(false, true, true));
        presetMap.put(NewManualFeedbackRequestNotification.class, createChannels(false, true, true));
        presetMap.put(ChannelDeletedNotification.class, createChannels(false, true, true));
        presetMap.put(AddedToChannelNotification.class, createChannels(false, true, true));
        presetMap.put(RemovedFromChannelNotification.class, createChannels(false, true, true));
        presetMap.put(DuplicateTestCaseNotification.class, createChannels(true, false, false));
        presetMap.put(NewCpcPlagiarismCaseNotification.class, createChannels(true, false, false));
        presetMap.put(NewPlagiarismCaseNotification.class, createChannels(true, false, false));
        presetMap.put(ProgrammingBuildRunUpdateNotification.class, createChannels(false, true, false));
        presetMap.put(ProgrammingTestCasesChangedNotification.class, createChannels(false, true, false));
        presetMap.put(PlagiarismCaseVerdictNotification.class, createChannels(true, true, true));
        presetMap.put(TutorialGroupAssignedNotification.class, createChannels(true, true, true));
        presetMap.put(TutorialGroupUnassignedNotification.class, createChannels(true, true, true));
        presetMap.put(RegisteredToTutorialGroupNotification.class, createChannels(true, true, true));
        presetMap.put(DeregisteredFromTutorialGroupNotification.class, createChannels(true, true, true));
        presetMap.put(TutorialGroupDeletedNotification.class, createChannels(true, true, true));
    }

    private Map<NotificationChannelOption, Boolean> createChannels(boolean email, boolean webapp, boolean push) {
        Map<NotificationChannelOption, Boolean> channelMap = new EnumMap<>(NotificationChannelOption.class);
        channelMap.put(NotificationChannelOption.EMAIL, email);
        channelMap.put(NotificationChannelOption.WEBAPP, webapp);
        channelMap.put(NotificationChannelOption.PUSH, push);
        return channelMap;
    }
}
