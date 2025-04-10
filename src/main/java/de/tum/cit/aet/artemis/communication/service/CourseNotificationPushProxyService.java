package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.communication.domain.NotificationType.ATTACHMENT_CHANGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_ADD_USER_CHANNEL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_DELETE_CHANNEL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_NEW_MESSAGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_NEW_REPLY_MESSAGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_REMOVE_USER_CHANNEL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_USER_MENTIONED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.DUPLICATE_TEST_CASE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_PRACTICE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_RELEASED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_UPDATED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_ANNOUNCEMENT_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_CPC_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_MANUAL_FEEDBACK_REQUEST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PLAGIARISM_CASE_VERDICT_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_BUILD_RUN_UPDATE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_TEST_CASES_CHANGED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.QUIZ_EXERCISE_STARTED;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.notification.NotificationTarget;
import de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSerializedDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.PushNotificationDataDTO;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Service responsible for transforming course notifications into a format compatible with native mobile devices.
 *
 * <p>
 * This service acts as a compatibility layer to translate course notifications into the expected format
 * for push notifications on mobile devices.
 * </p>
 *
 * <p>
 * Note: This service is intended as a temporary solution until both iOS and Android devices
 * can handle the new notification payload directly, at which point this service can be removed.
 * </p>
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationPushProxyService {

    /**
     * Converts a CourseNotificationDTO into a {@link PushNotificationDataDTO} suitable for native device consumption.
     *
     * <p>
     * This method handles the notification type-specific transformations, setting up the appropriate
     * placeholders, targets, and other metadata based on the notification type.
     * </p>
     *
     * @param courseNotificationDTO The course notification to transform
     * @return A {@link PushNotificationDataDTO} object containing the transformed notification data
     */
    public PushNotificationDataDTO fromCourseNotification(CourseNotificationDTO courseNotificationDTO) {
        String[] notificationPlaceholders;
        String target;
        String type;
        String date = courseNotificationDTO.creationDate().toString();
        int version = Constants.PUSH_NOTIFICATION_VERSION;
        Map<String, String> parameters = courseNotificationDTO.parameters().entrySet().stream().collect(HashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue())), HashMap::putAll);
        NotificationTarget notificationTarget;

        switch (courseNotificationDTO.notificationType()) {
            case "newMessageNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("postMarkdownContent"), courseNotificationDTO.creationDate().toString(),
                        parameters.get("channelName"), parameters.get("authorName"), parameters.get("channelType"), parameters.get("authorImageUrl"), parameters.get("authorId"),
                        parameters.get("postId"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.NEW_MESSAGE_TEXT, Long.parseLong(parameters.get("postId")),
                        NotificationTargetFactory.MESSAGE_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                notificationTarget.setConversationId(Long.parseLong(parameters.get("channelId")));
                target = notificationTarget.toJsonString();
                type = CONVERSATION_NEW_MESSAGE.toString();
                break;
            case "newAnswerNotification":
            case "newMentionNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("postMarkdownContent"), parameters.get("postCreationDate"),
                        parameters.get("postAuthorName"), parameters.get("replyMarkdownContent"), parameters.get("replyCreationDate"), parameters.get("replyAuthorName"),
                        parameters.get("channelName"), parameters.get("replyImageUrl"), parameters.get("replyAuthorId"), parameters.get("replyId"), parameters.get("postId"), };

                var isReply = parameters.get("replyId") != null;

                notificationTarget = new NotificationTarget(isReply ? NotificationTargetFactory.NEW_REPLY_TEXT : NotificationTargetFactory.NEW_MESSAGE_TEXT,
                        Long.parseLong(!isReply ? parameters.get("postId") : parameters.get("replyId")), NotificationTargetFactory.MESSAGE_TEXT, courseNotificationDTO.courseId(),
                        NotificationTargetFactory.COURSES_TEXT);
                notificationTarget.setConversationId(Long.parseLong(parameters.get("channelId")));
                target = notificationTarget.toJsonString();
                type = Objects.equals(courseNotificationDTO.notificationType(), "newAnswerNotification") ? CONVERSATION_NEW_REPLY_MESSAGE.toString()
                        : CONVERSATION_USER_MENTIONED.toString();
                break;
            case "newAnnouncementNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("postTitle"), parameters.get("postMarkdownContent"),
                        courseNotificationDTO.creationDate().toString(), parameters.get("authorName"), parameters.get("authorImageUrl"), parameters.get("authorId"),
                        parameters.get("postId"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.NEW_MESSAGE_TEXT, Long.parseLong(parameters.get("postId")),
                        NotificationTargetFactory.MESSAGE_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                notificationTarget.setConversationId(Long.parseLong(parameters.get("channelId")));
                target = notificationTarget.toJsonString();
                type = NEW_ANNOUNCEMENT_POST.toString();
                break;
            case "newExerciseNotification":
            case "exerciseOpenForPracticeNotification":
            case "exerciseUpdatedNotification":
            case "quizExerciseStartedNotification":
            case "newManualFeedbackRequestNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("exerciseTitle"), };

                var targetText = Objects.equals(courseNotificationDTO.notificationType(), "newExerciseNotification") ? NotificationTargetFactory.EXERCISE_RELEASED_TEXT
                        : NotificationTargetFactory.EXERCISE_UPDATED_TEXT;

                notificationTarget = new NotificationTarget(targetText, Long.parseLong(parameters.get("exerciseId")), NotificationTargetFactory.EXERCISES_TEXT,
                        courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = switch (courseNotificationDTO.notificationType()) {
                    case "newExerciseNotification" -> EXERCISE_RELEASED.toString();
                    case "exerciseOpenForPracticeNotification" -> EXERCISE_PRACTICE.toString();
                    case "exerciseUpdatedNotification" -> EXERCISE_UPDATED.toString();
                    case "newManualFeedbackRequestNotification" -> NEW_MANUAL_FEEDBACK_REQUEST.toString();
                    default -> QUIZ_EXERCISE_STARTED.toString();
                };
                break;
            case "exerciseAssessedNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("exerciseType"), parameters.get("exerciseTitle"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.EXERCISE_UPDATED_TEXT, Long.parseLong(parameters.get("exerciseId")),
                        NotificationTargetFactory.EXERCISES_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = EXERCISE_SUBMISSION_ASSESSED.toString();
                break;
            case "attachmentChangedNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("attachmentName"), parameters.get("unitName"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.ATTACHMENT_UPDATED_TEXT,
                        parameters.get("exerciseId") != null ? Long.parseLong(parameters.get("exerciseId")) : Long.parseLong(parameters.get("lectureId")),
                        parameters.get("exerciseId") != null ? NotificationTargetFactory.EXERCISES_TEXT : NotificationTargetFactory.LECTURES_TEXT, courseNotificationDTO.courseId(),
                        NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = ATTACHMENT_CHANGE.toString();
                break;
            case "channelDeletedNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("channelName"), parameters.get("deletingUser"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.CONVERSATION_DELETION_TEXT, Long.parseLong(parameters.get("courseId")),
                        NotificationTargetFactory.CONVERSATION_DELETION_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = CONVERSATION_DELETE_CHANNEL.toString();
                break;
            case "addedToChannelNotification":
            case "removedFromChannelNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("channelName"), parameters.get("channelModerator"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.COURSE_MANAGEMENT_TEXT, Long.parseLong(parameters.get("channelId")),
                        NotificationTargetFactory.COURSE_MANAGEMENT_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = courseNotificationDTO.notificationType().equals("addedToChannelNotification") ? CONVERSATION_ADD_USER_CHANNEL.toString()
                        : CONVERSATION_REMOVE_USER_CHANNEL.toString();
                break;
            case "duplicateTestCaseNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("exerciseTitle") };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.DUPLICATE_TEST_CASE_TEXT, Long.parseLong(parameters.get("exerciseId")),
                        NotificationTargetFactory.DUPLICATE_TEST_CASE_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = DUPLICATE_TEST_CASE.toString();
                break;
            case "newCpcPlagiarismCaseNotification":
            case "newPlagiarismCaseNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("exerciseType"), parameters.get("exerciseTitle"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.PLAGIARISM_DETECTED_TEXT, Long.parseLong(parameters.get("exerciseId")),
                        NotificationTargetFactory.PLAGIARISM_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = courseNotificationDTO.notificationType().equals("newCpcPlagiarismCaseNotification") ? NEW_CPC_PLAGIARISM_CASE_STUDENT.toString()
                        : NEW_PLAGIARISM_CASE_STUDENT.toString();
                break;
            case "programmingBuildRunUpdateNotification":
            case "programmingTestCasesChangedNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("exerciseTitle"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.PROGRAMMING_EXERCISES_TEXT, Long.parseLong(parameters.get("exerciseId")),
                        NotificationTargetFactory.PROGRAMMING_EXERCISES_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = courseNotificationDTO.notificationType().equals("programmingBuildRunUpdateNotification") ? PROGRAMMING_BUILD_RUN_UPDATE.toString()
                        : PROGRAMMING_TEST_CASES_CHANGED.toString();
                break;
            case "plagiarismCaseVerdictNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("exerciseType"), parameters.get("exerciseTitle"), };

                notificationTarget = new NotificationTarget(NotificationTargetFactory.PLAGIARISM_TEXT, Long.parseLong(parameters.get("exerciseId")),
                        NotificationTargetFactory.PLAGIARISM_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                target = notificationTarget.toJsonString();
                type = PLAGIARISM_CASE_VERDICT_STUDENT.toString();
                break;
            default:
                return new PushNotificationDataDTO(new CourseNotificationSerializedDTO(courseNotificationDTO));
        }

        return new PushNotificationDataDTO(replaceNullPlaceholders(notificationPlaceholders), target, type, date, version,
                new CourseNotificationSerializedDTO(courseNotificationDTO));
    }

    /**
     * Since the native applications expect an array of non-null strings, we need to replace null values with "".
     *
     * @param notificationPlaceholders the array of placeholders
     * @return the array of placeholders without any null values
     */
    private String[] replaceNullPlaceholders(String[] notificationPlaceholders) {
        return Arrays.stream(notificationPlaceholders).map((s) -> s == null ? "" : s).toArray(String[]::new);
    }
}
