package de.tum.cit.aet.artemis.domain.notification;

import static de.tum.cit.aet.artemis.domain.enumeration.NotificationPriority.HIGH;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_ADD_USER_CHANNEL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_ADD_USER_GROUP_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_CREATE_GROUP_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_CREATE_ONE_TO_ONE_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_DELETE_CHANNEL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_NEW_REPLY_MESSAGE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_REMOVE_USER_CHANNEL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_REMOVE_USER_GROUP_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_USER_MENTIONED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DATA_EXPORT_CREATED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DATA_EXPORT_FAILED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.FILE_SUBMISSION_SUCCESSFUL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_CPC_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_COURSE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXAM_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXERCISE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_LECTURE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.PLAGIARISM_CASE_VERDICT_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_ASSIGNED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_REGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UNASSIGNED;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_ADD_USER_CHANNEL_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_ADD_USER_GROUP_CHAT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_DELETE_CHANNEL_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_REMOVE_USER_CHANNEL_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_REMOVE_USER_GROUP_CHAT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.DATA_EXPORT_CREATED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.DATA_EXPORT_CREATED_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.DATA_EXPORT_FAILED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.DATA_EXPORT_FAILED_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.EXERCISE_SUBMISSION_ASSESSED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.EXERCISE_SUBMISSION_ASSESSED_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.FILE_SUBMISSION_SUCCESSFUL_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.FILE_SUBMISSION_SUCCESSFUL_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CHANNEL_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CONVERSATION_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_CPC_PLAGIARISM_CASE_STUDENT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_CPC_PLAGIARISM_CASE_STUDENT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_PLAGIARISM_CASE_STUDENT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_PLAGIARISM_CASE_STUDENT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.PLAGIARISM_CASE_VERDICT_STUDENT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.PLAGIARISM_CASE_VERDICT_STUDENT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_ASSIGNED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_REGISTRATION_STUDENT_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_REGISTRATION_TUTOR_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_UNASSIGNED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.findCorrespondingNotificationTitleOrThrow;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createConversationCreationTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createConversationDeletionTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createConversationMessageTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createDataExportCreatedTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createDataExportFailedTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createExerciseTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createMessageReplyTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createPlagiarismCaseTarget;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createTutorialGroupTarget;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.domain.DataExport;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.domain.metis.AnswerPost;
import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.metis.conversation.Conversation;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;

public class SingleUserNotificationFactory {

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param exercise         for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @param recipient        who should be notified
     * @return an instance of SingleUserNotification
     */
    public static @NotNull SingleUserNotification createNotification(Exercise exercise, NotificationType notificationType, User recipient) {
        String title;
        String notificationText;
        String[] placeholderValues;
        SingleUserNotification notification;
        switch (notificationType) {
            case EXERCISE_SUBMISSION_ASSESSED -> {
                title = EXERCISE_SUBMISSION_ASSESSED_TITLE;
                notificationText = EXERCISE_SUBMISSION_ASSESSED_TEXT;
                placeholderValues = createPlaceholdersExerciseSubmissionAssessed(exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        exercise.getExerciseType().getExerciseTypeAsReadableString(), exercise.getTitle());
            }
            case FILE_SUBMISSION_SUCCESSFUL -> {
                title = FILE_SUBMISSION_SUCCESSFUL_TITLE;
                notificationText = FILE_SUBMISSION_SUCCESSFUL_TEXT;
                placeholderValues = createPlaceholdersFileSubmissionSuccessful(exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(), exercise.getTitle());
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        notification = new SingleUserNotification(recipient, title, notificationText, true, placeholderValues);
        notification.setTransientAndStringTarget(createExerciseTarget(exercise, title));
        return notification;
    }

    @NotificationPlaceholderCreator(values = { EXERCISE_SUBMISSION_ASSESSED })
    public static String[] createPlaceholdersExerciseSubmissionAssessed(String courseTitle, String exerciseType, String exerciseTitle) {
        return new String[] { courseTitle, exerciseType, exerciseTitle };
    }

    @NotificationPlaceholderCreator(values = { FILE_SUBMISSION_SUCCESSFUL })
    public static String[] createPlaceholdersFileSubmissionSuccessful(String courseTitle, String exerciseTitle) {
        return new String[] { courseTitle, exerciseTitle };
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
        var notification = new SingleUserNotification(recipient, title, text, true, createPlaceholdersDataExport());
        if (type == NotificationType.DATA_EXPORT_FAILED) {
            notification.setPriority(HIGH);
            notification.setTransientAndStringTarget(createDataExportFailedTarget(path));
        }
        else {
            notification.setTransientAndStringTarget(createDataExportCreatedTarget(dataExport, path));
        }
        return notification;
    }

    @NotificationPlaceholderCreator(values = { DATA_EXPORT_CREATED, DATA_EXPORT_FAILED })
    public static String[] createPlaceholdersDataExport() {
        return new String[] {};
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
                placeholderValues = createPlaceholdersPlagiarism(affectedExercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        affectedExercise.getExerciseType().toString().toLowerCase(), affectedExercise.getTitle());
            }
            case NEW_CPC_PLAGIARISM_CASE_STUDENT -> {
                title = NEW_CPC_PLAGIARISM_CASE_STUDENT_TITLE;
                notificationText = NEW_CPC_PLAGIARISM_CASE_STUDENT_TEXT;
                placeholderValues = createPlaceholdersPlagiarism(affectedExercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        affectedExercise.getExerciseType().toString().toLowerCase(), affectedExercise.getTitle());
            }
            case PLAGIARISM_CASE_VERDICT_STUDENT -> {
                title = PLAGIARISM_CASE_VERDICT_STUDENT_TITLE;
                notificationText = PLAGIARISM_CASE_VERDICT_STUDENT_TEXT;
                placeholderValues = createPlaceholdersPlagiarism(affectedExercise.getCourseViaExerciseGroupOrCourseMember().getTitle(),
                        affectedExercise.getExerciseType().toString().toLowerCase(), affectedExercise.getTitle());
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

    @NotificationPlaceholderCreator(values = { NEW_PLAGIARISM_CASE_STUDENT, NEW_CPC_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT })
    public static String[] createPlaceholdersPlagiarism(String courseTitle, String exerciseType, String exerciseTitle) {
        return new String[] { courseTitle, exerciseType, exerciseTitle };
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
                        createPlaceholderTutorialGroup(tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName()));
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false, true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_STUDENT -> {
                var student = users.stream().findAny().orElseThrow();
                notification = new SingleUserNotification(student, title, TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TEXT, true,
                        createPlaceholderTutorialGroup(tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName()));
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false, true));
            }
            case TUTORIAL_GROUP_REGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                var student = users.stream().findAny();
                var studentName = student.isPresent() ? student.get().getName() : "";

                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, TUTORIAL_GROUP_REGISTRATION_TUTOR_TEXT, true,
                        createPlaceholderTutorRegistration(tutorialGroup.getCourse().getTitle(), studentName, tutorialGroup.getTitle(), responsibleForAction.getName()));
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }

                var student = users.stream().findAny();
                var studentName = student.isPresent() ? student.get().getName() : "";

                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TEXT, true,
                        createPlaceholderTutorRegistration(tutorialGroup.getCourse().getTitle(), studentName, tutorialGroup.getTitle(), responsibleForAction.getName()));
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR -> {
                if (tutorialGroup.getTeachingAssistant() == null) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TEXT, true,
                        createPlaceholderTutorRegistrationMultiple(tutorialGroup.getCourse().getTitle(), Integer.toString(users.size()), tutorialGroup.getTitle(),
                                responsibleForAction.getName()));

                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_ASSIGNED -> {
                var tutorToContact = users.stream().findAny().get();
                notification = new SingleUserNotification(tutorToContact, title, TUTORIAL_GROUP_ASSIGNED_TEXT, true,
                        createPlaceholderTutorialGroup(tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName()));
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            case TUTORIAL_GROUP_UNASSIGNED -> {
                var tutorToContact = users.stream().findAny().get();
                notification = new SingleUserNotification(tutorToContact, title, TUTORIAL_GROUP_UNASSIGNED_TEXT, true,
                        createPlaceholderTutorialGroup(tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle(), responsibleForAction.getName()));
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true, true));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

    @NotificationPlaceholderCreator(values = { TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED })
    public static String[] createPlaceholderTutorialGroup(String courseTitle, String tutorialGroupTitle, String responsibleForUserName) {
        return new String[] { courseTitle, tutorialGroupTitle, responsibleForUserName };
    }

    @NotificationPlaceholderCreator(values = { TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR })
    public static String[] createPlaceholderTutorRegistration(String courseTitle, String studentName, String tutorialGroupTitle, String responsibleForUserName) {
        return new String[] { courseTitle, studentName, tutorialGroupTitle, responsibleForUserName };
    }

    @NotificationPlaceholderCreator(values = { TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR })
    public static String[] createPlaceholderTutorRegistrationMultiple(String courseTitle, String userSize, String tutorialGroupTitle, String responsibleForUserName) {
        return new String[] { courseTitle, userSize, tutorialGroupTitle, responsibleForUserName };
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
        var placeholders = createPlaceholdersNewReply(conversation.getCourse().getTitle(), answerPost.getPost().getContent(), answerPost.getPost().getCreationDate().toString(),
                answerPost.getPost().getAuthor().getName(), answerPost.getContent(), answerPost.getCreationDate().toString(), answerPost.getAuthor().getName(),
                conversation.getHumanReadableNameForReceiver(answerPost.getAuthor()));

        String messageReplyTextType = MESSAGE_REPLY_IN_CONVERSATION_TEXT;

        if (conversation instanceof Channel) {
            messageReplyTextType = MESSAGE_REPLY_IN_CHANNEL_TEXT;
        }

        SingleUserNotification notification = new SingleUserNotification(user, title, messageReplyTextType, true, placeholders);
        notification.setTransientAndStringTarget(createMessageReplyTarget(answerPost, conversation.getCourse().getId()));
        notification.setAuthor(responsibleForAction);
        return notification;
    }

    @NotificationPlaceholderCreator(values = { NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_EXAM_POST,
            CONVERSATION_NEW_REPLY_MESSAGE, CONVERSATION_USER_MENTIONED })
    public static String[] createPlaceholdersNewReply(String courseTitle, String postContent, String postCreationData, String postAuthorName, String answerPostContent,
            String answerPostCreationDate, String authorName, String conversationName) {
        return new String[] { courseTitle, postContent, postCreationData, postAuthorName, answerPostContent, answerPostCreationDate, authorName, conversationName };
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
            case CONVERSATION_CREATE_ONE_TO_ONE_CHAT -> // text is null because the notification is not shown
                new SingleUserNotification(user, title, null, false, createPlaceholdersConversationCreateOneToOneChat())
                        .transientAndStringTarget(createConversationCreationTarget(conversation, conversation.getCourse().getId()));
            case CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT -> {
                String[] placeholders = createPlaceholdersForUserGroupChat(conversation.getCourse().getTitle(), responsibleForAction.getName());
                yield new SingleUserNotification(user, title, CONVERSATION_ADD_USER_GROUP_CHAT_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationCreationTarget(conversation, conversation.getCourse().getId()));
            }
            case CONVERSATION_ADD_USER_CHANNEL -> {
                var channel = (Channel) conversation;
                String[] placeholders = createPlaceholdersForUserChannel(channel.getCourse().getTitle(), channel.getName(), responsibleForAction.getName());
                yield new SingleUserNotification(user, title, CONVERSATION_ADD_USER_CHANNEL_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationCreationTarget(channel, channel.getCourse().getId()));
            }
            case CONVERSATION_REMOVE_USER_CHANNEL -> {
                var channel = (Channel) conversation;
                String[] placeholders = createPlaceholdersForUserChannel(channel.getCourse().getTitle(), channel.getName(), responsibleForAction.getName());
                yield new SingleUserNotification(user, title, CONVERSATION_REMOVE_USER_CHANNEL_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationDeletionTarget(channel, channel.getCourse().getId()));
            }
            case CONVERSATION_REMOVE_USER_GROUP_CHAT -> {
                String[] placeholders = createPlaceholdersForUserGroupChat(conversation.getCourse().getTitle(), responsibleForAction.getName());
                yield new SingleUserNotification(user, title, CONVERSATION_REMOVE_USER_GROUP_CHAT_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationDeletionTarget(conversation, conversation.getCourse().getId()));
            }
            case CONVERSATION_DELETE_CHANNEL -> {
                var channel = (Channel) conversation;
                String[] placeholders = createPlaceholdersForUserChannel(channel.getCourse().getTitle(), channel.getName(), responsibleForAction.getName());
                yield new SingleUserNotification(user, title, CONVERSATION_DELETE_CHANNEL_TEXT, true, placeholders)
                        .transientAndStringTarget(createConversationDeletionTarget(channel, channel.getCourse().getId()));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        };
        notification.setAuthor(responsibleForAction);

        return notification;
    }

    @NotificationPlaceholderCreator(values = { CONVERSATION_CREATE_ONE_TO_ONE_CHAT })
    public static String[] createPlaceholdersConversationCreateOneToOneChat() {
        return new String[] {};
    }

    @NotificationPlaceholderCreator(values = { CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT, CONVERSATION_REMOVE_USER_GROUP_CHAT })
    public static String[] createPlaceholdersForUserGroupChat(String courseTitle, String responsibleForUserName) {
        return new String[] { courseTitle, responsibleForUserName };
    }

    @NotificationPlaceholderCreator(values = { CONVERSATION_ADD_USER_CHANNEL, CONVERSATION_REMOVE_USER_CHANNEL, CONVERSATION_DELETE_CHANNEL })
    public static String[] createPlaceholdersForUserChannel(String courseTitle, String channelName, String responsibleForUserName) {
        return new String[] { courseTitle, channelName, responsibleForUserName };
    }
}
