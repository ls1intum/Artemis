package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.HIGH;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

public class SingleUserNotificationFactory {

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param exercise         for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @param recipient        who should be notified
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Exercise exercise, NotificationType notificationType, User recipient) {
        String title;
        String notificationText;
        String[] placeholderValues;
        SingleUserNotification notification;
        switch (notificationType) {
            case EXERCISE_SUBMISSION_ASSESSED -> {
                title = EXERCISE_SUBMISSION_ASSESSED_TITLE;
                notificationText = EXERCISE_SUBMISSION_ASSESSED_TEXT;
                placeholderValues = new String[] { exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(), exercise.getExerciseType().getExerciseTypeAsReadableString(),
                        exercise.getTitle() };
            }
            case FILE_SUBMISSION_SUCCESSFUL -> {
                title = FILE_SUBMISSION_SUCCESSFUL_TITLE;
                notificationText = FILE_SUBMISSION_SUCCESSFUL_TEXT;
                placeholderValues = new String[] { exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(), exercise.getTitle() };
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        notification = new SingleUserNotification(recipient, title, notificationText, true, placeholderValues);
        notification.setTransientAndStringTarget(createExerciseTarget(exercise, title));
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification for a successful data export creation.
     *
     * @param dataExport the data export that was created
     * @param type       the type of the notification
     * @param recipient  the user that should be notified (the requester of the data export)
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(DataExport dataExport, NotificationType type, User recipient) {
        var title = DATA_EXPORT_CREATED_TITLE;
        var text = DATA_EXPORT_CREATED_TEXT;
        var path = "data-exports";
        if (type == NotificationType.DATA_EXPORT_FAILED) {
            title = DATA_EXPORT_FAILED_TITLE;
            text = DATA_EXPORT_FAILED_TEXT;
        }
        var notification = new SingleUserNotification(recipient, title, text, true, new String[0]);
        if (type == NotificationType.DATA_EXPORT_FAILED) {
            notification.setPriority(HIGH);
            notification.setTransientAndStringTarget(createDataExportFailedTarget(path));
        }
        else {
            notification.setTransientAndStringTarget(createDataExportCreatedTarget(dataExport, path));
        }
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification based on plagiarisms.
     *
     * @param plagiarismCase   that hold the major information for the plagiarism case
     * @param notificationType type of the notification that should be created
     * @param student          who should be notified or is the author (depends if the student or instructor should be notified)
     * @param instructor       who should be notified or is the author
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(PlagiarismCase plagiarismCase, NotificationType notificationType, User student, User instructor) {
        String title;
        String notificationText;
        String[] placeholderValues;
        SingleUserNotification notification;
        Long courseId;
        Exercise affectedExercise = plagiarismCase.getExercise();

        switch (notificationType) {
            case NEW_PLAGIARISM_CASE_STUDENT -> {
                title = NEW_PLAGIARISM_CASE_STUDENT_TITLE;
                notificationText = NEW_PLAGIARISM_CASE_STUDENT_TEXT;
                placeholderValues = new String[] { affectedExercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        affectedExercise.getExerciseType().toString().toLowerCase(), affectedExercise.getTitle() };
            }
            case NEW_CPC_PLAGIARISM_CASE_STUDENT -> {
                title = NEW_CPC_PLAGIARISM_CASE_STUDENT_TITLE;
                notificationText = NEW_CPC_PLAGIARISM_CASE_STUDENT_TEXT;
                placeholderValues = new String[] { affectedExercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        affectedExercise.getExerciseType().toString().toLowerCase(), affectedExercise.getTitle() };
            }
            case PLAGIARISM_CASE_VERDICT_STUDENT -> {
                title = PLAGIARISM_CASE_VERDICT_STUDENT_TITLE;
                notificationText = PLAGIARISM_CASE_VERDICT_STUDENT_TEXT;
                placeholderValues = new String[] { affectedExercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        affectedExercise.getExerciseType().toString().toLowerCase(), affectedExercise.getTitle() };
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        courseId = affectedExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        notification = new SingleUserNotification(student, title, notificationText, true, placeholderValues);
        notification.setPriority(HIGH);
        notification.setAuthor(instructor);
        notification.setTransientAndStringTarget(createPlagiarismCaseTarget(plagiarismCase.getId(), courseId));
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification for tutorial groups.
     *
     * @param tutorialGroup        to which the notification is related
     * @param notificationType     type of the notification that should be created
     * @param users                who should be notified or are related to the notification
     * @param responsibleForAction the user who is responsible for the action that triggered the notification
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(TutorialGroup tutorialGroup, NotificationType notificationType, Set<User> users, User responsibleForAction) {
        var title = findCorrespondingNotificationTitleOrThrow(notificationType);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("No users provided for notification");
        }
        SingleUserNotification notification;
        switch (notificationType) {
            case TUTORIAL_GROUP_REGISTRATION_STUDENT -> {
                var student = users.stream().findAny().orElseThrow();
                notification = new SingleUserNotification(student, title, TUTORIAL_GROUP_REGISTRATION_STUDENT_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName() });
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false, true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_STUDENT -> {
                var student = users.stream().findAny().orElseThrow();
                notification = new SingleUserNotification(student, title, TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName() });
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false, true));
            }
            case TUTORIAL_GROUP_REGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                var student = users.stream().findAny();
                var studentName = student.isPresent() ? student.get().getName() : "";

                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, TUTORIAL_GROUP_REGISTRATION_TUTOR_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), studentName, tutorialGroup.getTitle(), responsibleForAction.getName() });
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }

                var student = users.stream().findAny();
                var studentName = student.isPresent() ? student.get().getName() : "";

                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), studentName, tutorialGroup.getTitle(), responsibleForAction.getName() });
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), Integer.toString(users.size()), tutorialGroup.getTitle(), responsibleForAction.getName() });

                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_ASSIGNED -> {
                var tutorToContact = users.stream().findAny().get();
                notification = new SingleUserNotification(tutorToContact, title, TUTORIAL_GROUP_ASSIGNED_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName() });
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_UNASSIGNED -> {
                var tutorToContact = users.stream().findAny().get();
                notification = new SingleUserNotification(tutorToContact, title, TUTORIAL_GROUP_UNASSIGNED_TEXT, true,
                        new String[] { tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName() });
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification for message replies.
     *
     * @param answerPost           to which the notification is related
     * @param notificationType     type of the notification that should be created
     * @param user                 who should be notified or are related to the notification
     * @param responsibleForAction the user who is responsible for the action that triggered the notification
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(AnswerPost answerPost, NotificationType notificationType, User user, User responsibleForAction) {
        String title = findCorrespondingNotificationTitleOrThrow(notificationType);
        if (user == null) {
            throw new IllegalArgumentException("No users provided for notification");
        }

        Conversation conversation = answerPost.getPost().getConversation();
        List<String> placeholders = new ArrayList<>();
        placeholders.add(conversation.getCourse().getTitle());
        placeholders.add(answerPost.getPost().getContent());
        placeholders.add(answerPost.getPost().getCreationDate().toString());
        placeholders.add(answerPost.getPost().getAuthor().getName());
        placeholders.add(answerPost.getContent());
        placeholders.add(answerPost.getCreationDate().toString());
        placeholders.add(answerPost.getAuthor().getName());
        String messageReplyTextType = MESSAGE_REPLY_IN_CONVERSATION_TEXT;

        if (conversation instanceof Channel channel) {
            placeholders.add(channel.getName());
            messageReplyTextType = MESSAGE_REPLY_IN_CHANNEL_TEXT;
        }

        SingleUserNotification notification = new SingleUserNotification(user, title, messageReplyTextType, true, placeholders.toArray(String[]::new));
        notification.setTransientAndStringTarget(createMessageReplyTarget(answerPost, conversation.getCourse().getId()));
        notification.setAuthor(responsibleForAction);
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification for messages where the recipient is mentioned.
     *
     * @param message          to which the notification is related
     * @param notificationType type of the notification that should be created
     * @param notificationText placeholder text key
     * @param placeholders     placeholders for the text
     * @param recipient        who should be notified or are related to the notification
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Post message, NotificationType notificationType, String notificationText, String[] placeholders, User recipient) {
        String title = findCorrespondingNotificationTitleOrThrow(notificationType);
        if (recipient == null) {
            throw new IllegalArgumentException("No users provided for notification");
        }

        if (notificationType != NotificationType.CONVERSATION_USER_MENTIONED && notificationType != NotificationType.NEW_ANNOUNCEMENT_POST) {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        SingleUserNotification notification = new SingleUserNotification(recipient, title, notificationText, true, placeholders);
        notification.setTransientAndStringTarget(createConversationMessageTarget(message, message.getConversation().getCourse().getId()));
        notification.setAuthor(message.getAuthor());
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification for conversation creation or deletion.
     *
     * @param conversation         to which the notification is related
     * @param notificationType     type of the notification that should be created
     * @param user                 who should be notified or are related to the notification
     * @param responsibleForAction the user who is responsible for the action that triggered the notification
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Conversation conversation, NotificationType notificationType, User user, User responsibleForAction) {
        String title = findCorrespondingNotificationTitleOrThrow(notificationType);
        if (user == null) {
            throw new IllegalArgumentException("No user provided for notification");
        }
        SingleUserNotification notification = switch (notificationType) {
            case CONVERSATION_CREATE_ONE_TO_ONE_CHAT -> {
                // text is null because the notification is not shown
                yield new SingleUserNotification(user, title, null, false, new String[] {})
                        .transientAndStringTarget(createConversationCreationTarget(conversation, conversation.getCourse().getId()));
            }
            case CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT -> {
                String[] placeholders = new String[] { conversation.getCourse().getTitle(), responsibleForAction.getName() };
                yield new SingleUserNotification(user, title, CONVERSATION_ADD_USER_GROUP_CHAT_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationCreationTarget(conversation, conversation.getCourse().getId()));
            }
            case CONVERSATION_ADD_USER_CHANNEL -> {
                var channel = (Channel) conversation;
                String[] placeholders = new String[] { channel.getCourse().getTitle(), channel.getName(), responsibleForAction.getName() };
                yield new SingleUserNotification(user, title, CONVERSATION_ADD_USER_CHANNEL_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationCreationTarget(channel, channel.getCourse().getId()));
            }
            case CONVERSATION_REMOVE_USER_CHANNEL -> {
                var channel = (Channel) conversation;
                String[] placeholders = new String[] { channel.getCourse().getTitle(), channel.getName(), responsibleForAction.getName() };
                yield new SingleUserNotification(user, title, CONVERSATION_REMOVE_USER_CHANNEL_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationDeletionTarget(channel, channel.getCourse().getId()));
            }
            case CONVERSATION_REMOVE_USER_GROUP_CHAT -> {
                String[] placeholders = new String[] { conversation.getCourse().getTitle(), responsibleForAction.getName() };
                yield new SingleUserNotification(user, title, CONVERSATION_REMOVE_USER_GROUP_CHAT_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationDeletionTarget(conversation, conversation.getCourse().getId()));
            }
            case CONVERSATION_DELETE_CHANNEL -> {
                var channel = (Channel) conversation;
                String[] placeholders = new String[] { channel.getCourse().getTitle(), channel.getName(), responsibleForAction.getName() };
                yield new SingleUserNotification(user, title, CONVERSATION_DELETE_CHANNEL_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationDeletionTarget(channel, channel.getCourse().getId()));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        };
        notification.setAuthor(responsibleForAction);

        return notification;
    }

}
