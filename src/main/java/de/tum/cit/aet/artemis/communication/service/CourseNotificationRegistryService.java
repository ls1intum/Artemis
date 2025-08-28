package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.AddedToChannelNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.AttachmentChangedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ChannelDeletedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;
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

/**
 * Registry service that discovers and maps all {@link CourseNotification}
 * subtypes annotated with {@link CourseNotificationType} during application startup.
 * This registry serves as a central mapping between database tinyint values and their
 * corresponding notification class types, eliminating the need for manual registration
 * of new notification types.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationRegistryService {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationRegistryService.class);

    private final Map<Short, Class<? extends CourseNotification>> notificationTypes = new HashMap<>();

    private final Map<Class<? extends CourseNotification>, Short> notificationTypeIdentifiers = new HashMap<>();

    /**
     * Constructs a new NotificationRegistry and explicitly registers all notification types.
     * This replaces classpath scanning to ensure compatibility with GraalVM native images.
     */
    public CourseNotificationRegistryService() {
        registerNotification(NewPostNotification.class);
        registerNotification(NewAnswerNotification.class);
        registerNotification(NewMentionNotification.class);
        registerNotification(NewAnnouncementNotification.class);
        registerNotification(NewExerciseNotification.class);
        registerNotification(ExerciseOpenForPracticeNotification.class);
        registerNotification(ExerciseAssessedNotification.class);
        registerNotification(ExerciseUpdatedNotification.class);
        registerNotification(QuizExerciseStartedNotification.class);
        registerNotification(AttachmentChangedNotification.class);
        registerNotification(NewManualFeedbackRequestNotification.class);
        registerNotification(DuplicateTestCaseNotification.class);
        registerNotification(NewCpcPlagiarismCaseNotification.class);
        registerNotification(NewPlagiarismCaseNotification.class);
        registerNotification(ProgrammingBuildRunUpdateNotification.class);
        registerNotification(ProgrammingTestCasesChangedNotification.class);
        registerNotification(PlagiarismCaseVerdictNotification.class);
        registerNotification(ChannelDeletedNotification.class);
        registerNotification(AddedToChannelNotification.class);
        registerNotification(RemovedFromChannelNotification.class);
        registerNotification(TutorialGroupAssignedNotification.class);
        registerNotification(TutorialGroupDeletedNotification.class);
        registerNotification(RegisteredToTutorialGroupNotification.class);
        registerNotification(DeregisteredFromTutorialGroupNotification.class);
        registerNotification(TutorialGroupUnassignedNotification.class);
    }

    private void registerNotification(Class<? extends CourseNotification> notificationClass) {
        CourseNotificationType annotation = notificationClass.getAnnotation(CourseNotificationType.class);
        if (annotation != null) {
            Short typeId = (short) annotation.value();
            log.debug("Registering notification: {}, {}", typeId, notificationClass);
            notificationTypes.put(typeId, notificationClass);
            notificationTypeIdentifiers.put(notificationClass, typeId);
        }
    }

    /**
     * Returns a list of all registered notifications in camelCase format.
     *
     * @return map of all notifications in camelCase format.
     */
    public Map<Short, String> getNotificationTypes() {
        Map<Short, String> result = new HashMap<>();

        for (Map.Entry<Short, Class<? extends CourseNotification>> entry : notificationTypes.entrySet()) {
            Short key = entry.getKey();
            Class<?> clazz = entry.getValue();

            String className = clazz.getSimpleName();

            className = Character.toLowerCase(className.charAt(0)) + className.substring(1);

            result.put(key, className);
        }

        return result;
    }

    /**
     * Retrieves the notification class type that corresponds to the given database type identifier.
     *
     * @param typeId the database tinyint value representing a notification type
     * @return the corresponding notification class, or null if no mapping exists for the given typeId
     */
    protected Class<? extends CourseNotification> getNotificationClass(Short typeId) {
        return notificationTypes.get(typeId);
    }

    /**
     * Retrieves the notification class type identifier that corresponds to the given class. If this returns null
     * for your given notification, this means you might have forgotten to add the {@link CourseNotificationType}
     * annotation to your notification.
     *
     * @param typeClass the class of a notification type
     * @return the corresponding database identifier, or null if no mapping exists for the given class
     */
    protected Short getNotificationIdentifier(Class<? extends CourseNotification> typeClass) {
        return notificationTypeIdentifiers.get(typeClass);
    }
}
