package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

public class SingleUserNotificationFactory {

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param post             which is answered
     * @param notificationType type of the notification that should be created
     * @param course           that the post belongs to
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Post post, NotificationType notificationType, Course course) {
        User recipient = post.getAuthor();
        String title;
        SingleUserNotification notification;
        switch (notificationType) {
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                title = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTransientAndStringTarget(createExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                title = NEW_REPLY_FOR_LECTURE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTransientAndStringTarget(createLecturePostTarget(post, course));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NEW_REPLY_FOR_COURSE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

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
        SingleUserNotification notification;
        switch (notificationType) {
            case EXERCISE_SUBMISSION_ASSESSED -> {
                title = EXERCISE_SUBMISSION_ASSESSED_TITLE;
                notificationText = "Your submission for the " + exercise.getExerciseType().getExerciseTypeAsReadableString() + " exercise \"" + exercise.getTitle()
                        + "\" has been assessed.";
            }
            case FILE_SUBMISSION_SUCCESSFUL -> {
                title = FILE_SUBMISSION_SUCCESSFUL_TITLE;
                notificationText = "Your file for the exercise \"" + exercise.getTitle() + "\" was successfully submitted.";
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        notification = new SingleUserNotification(recipient, title, notificationText);
        notification.setTransientAndStringTarget(createExerciseTarget(exercise, title));
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
        SingleUserNotification notification;
        Long courseId;
        Exercise affectedExercise = plagiarismCase.getExercise();

        switch (notificationType) {
            case NEW_PLAGIARISM_CASE_STUDENT -> {
                title = NEW_PLAGIARISM_CASE_STUDENT_TITLE;
                notificationText = "New plagiarism case concerning the " + affectedExercise.getExerciseType().toString().toLowerCase() + " exercise \""
                        + affectedExercise.getTitle() + "\".";
            }
            case PLAGIARISM_CASE_VERDICT_STUDENT -> {
                title = PLAGIARISM_CASE_VERDICT_STUDENT_TITLE;
                notificationText = "Your plagiarism case concerning the " + affectedExercise.getExerciseType().toString().toLowerCase() + " exercise \""
                        + affectedExercise.getTitle() + "\"" + " has a verdict.";
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        courseId = affectedExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        notification = new SingleUserNotification(student, title, notificationText);
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
                notification = new SingleUserNotification(student, title,
                        "You have been registered to the tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false, true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_STUDENT -> {
                var student = users.stream().findAny().orElseThrow();
                notification = new SingleUserNotification(student, title,
                        "You have been deregistered from the tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false, true));
            }
            case TUTORIAL_GROUP_REGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                var student = users.stream().findAny();
                var studentName = student.isPresent() ? student.get().getName() : "";

                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title,
                        "The student " + studentName + " has been registered to your tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }

                var student = users.stream().findAny();
                var studentName = student.isPresent() ? student.get().getName() : "";

                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, "The student " + studentName
                        + " has been deregistered from your tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title,
                        users.size() + " students have been registered to your tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");

                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_ASSIGNED -> {
                var tutorToContact = users.stream().findAny().get();
                notification = new SingleUserNotification(tutorToContact, title,
                        "You have been assigned to lead the tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_UNASSIGNED -> {
                var tutorToContact = users.stream().findAny().get();
                notification = new SingleUserNotification(tutorToContact, title,
                        "You have been unassigned from leading the tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
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

        if (notificationType != NotificationType.CONVERSATION_NEW_REPLY_MESSAGE) {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        String text = "You have new reply in a message by " + responsibleForAction.getName() + " in course (" + answerPost.getPost().getConversation().getCourse().getTitle()
                + ").";
        SingleUserNotification notification = new SingleUserNotification(user, title, text);
        notification.setTransientAndStringTarget(createMessageReplyTarget(answerPost, answerPost.getPost().getConversation().getCourse().getId()));
        notification.setAuthor(responsibleForAction);
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
        SingleUserNotification notification;
        switch (notificationType) {
            case CONVERSATION_CREATE_ONE_TO_ONE_CHAT -> {
                OneToOneChat oneToOneChat = (OneToOneChat) conversation;
                // text is null because the notification is not shown
                notification = new SingleUserNotification(user, title, null);
                notification.setTransientAndStringTarget(createConversationCreationTarget(oneToOneChat, oneToOneChat.getCourse().getId()));
                notification.setAuthor(responsibleForAction);
            }
            case CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT -> {
                var groupChat = (GroupChat) conversation;
                String text = "You have been added to a new group chat by " + responsibleForAction.getName() + " in course (" + groupChat.getCourse().getTitle() + ").";
                notification = new SingleUserNotification(user, title, text);
                notification.setTransientAndStringTarget(createConversationCreationTarget(groupChat, groupChat.getCourse().getId()));
                notification.setAuthor(responsibleForAction);
            }
            case CONVERSATION_ADD_USER_CHANNEL -> {
                var channel = (Channel) conversation;
                String text = "You have been added to channel (" + channel.getName() + ") by " + responsibleForAction.getName() + " in course (" + channel.getCourse().getTitle()
                        + ").";
                notification = new SingleUserNotification(user, title, text);
                notification.setTransientAndStringTarget(createConversationCreationTarget(channel, channel.getCourse().getId()));
                notification.setAuthor(responsibleForAction);
            }
            case CONVERSATION_REMOVE_USER_CHANNEL -> {
                var channel = (Channel) conversation;
                String text = "You have been removed from channel (" + channel.getName() + ") by " + responsibleForAction.getName() + " in course ("
                        + channel.getCourse().getTitle() + ").";
                notification = new SingleUserNotification(user, title, text);
                notification.setTransientAndStringTarget(createConversationDeletionTarget(channel, channel.getCourse().getId()));
                notification.setAuthor(responsibleForAction);
            }
            case CONVERSATION_REMOVE_USER_GROUP_CHAT -> {
                var groupChat = (GroupChat) conversation;
                String text = "You have been removed from group chat by " + responsibleForAction.getName() + " in course (" + groupChat.getCourse().getTitle() + ").";
                notification = new SingleUserNotification(user, title, text);
                notification.setTransientAndStringTarget(createConversationDeletionTarget(groupChat, groupChat.getCourse().getId()));
                notification.setAuthor(responsibleForAction);
            }
            case CONVERSATION_DELETE_CHANNEL -> {
                var channel = (Channel) conversation;
                String text = channel.getName() + " channel has been deleted by " + responsibleForAction.getName() + " in course (" + channel.getCourse().getTitle() + ").";
                notification = new SingleUserNotification(user, title, text);
                notification.setTransientAndStringTarget(createConversationDeletionTarget(channel, channel.getCourse().getId()));
                notification.setAuthor(responsibleForAction);
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

}
