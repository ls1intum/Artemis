package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

public class NotificationTargetFactory {

    // shared constants

    public static final String COURSES_TEXT = "courses";

    public static final String PROGRAMMING_EXERCISES_TEXT = "programming-exercises";

    public static final String COURSE_MANAGEMENT_TEXT = "course-management";

    public static final String EXERCISES_TEXT = "exercises";

    public static final String EXAMS_TEXT = "exams";

    public static final String LECTURES_TEXT = "lectures";

    public static final String TUTORIAL_GROUP_MANAGEMENT_TEXT = "tutorial-groups-management";

    public static final String TUTORIAL_GROUPS_TEXT = "tutorial-groups";

    public static final String NEW_MESSAGE_TEXT = "new-message";

    public static final String NEW_REPLY_TEXT = "new-reply";

    public static final String MESSAGE_TEXT = "message";

    public static final String CONVERSATION_TEXT = "conversation";

    public static final String CONVERSATION_CREATION_TEXT = "conversation-creation";

    public static final String CONVERSATION_DELETION_TEXT = "conversation-deletion";

    public static final String ATTACHMENT_UPDATED_TEXT = "attachmentUpdated";

    public static final String EXERCISE_RELEASED_TEXT = "exerciseReleased";

    public static final String EXERCISE_UPDATED_TEXT = "exerciseUpdated";

    public static final String DUPLICATE_TEST_CASE_TEXT = "duplicateTestCase";

    public static final String COURSE_ARCHIVE_UPDATED_TEXT = "courseArchiveUpdated";

    public static final String EXAM_ARCHIVE_UPDATED_TEXT = "examArchiveUpdated";

    public static final String PLAGIARISM_TEXT = "plagiarism-cases";

    public static final String PLAGIARISM_DETECTED_TEXT = "plagiarismDetected";

    public static final String PRIVACY = "privacy";

    // EXERCISE related targets

    /**
     * Create the needed target for "ExerciseReleased" notifications
     *
     * @param exercise that was released
     * @return the final target property
     */
    public static NotificationTarget createExerciseReleasedTarget(Exercise exercise) {
        return createExerciseTarget(exercise, EXERCISE_RELEASED_TEXT);
    }

    /**
     * Create the needed target for "ExerciseUpdated" notifications
     *
     * @param exercise that was updated
     * @return the final target property
     */
    public static NotificationTarget createExerciseUpdatedTarget(Exercise exercise) {
        return createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
    }

    /**
     * Create a NotificationTarget for a GroupNotification for an ProgrammingExercise in an Exam or if duplicated test cases were detected.
     *
     * @param programmingExercise for which to create the notification
     * @param message             to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createExamProgrammingExerciseOrTestCaseTarget(ProgrammingExercise programmingExercise, String message) {
        NotificationTarget target = new NotificationTarget(PROGRAMMING_EXERCISES_TEXT, programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId(),
                COURSE_MANAGEMENT_TEXT);
        target.setIdentifier(programmingExercise.getId());
        target.setMessage(message);
        return target;
    }

    /**
     * Create a NotificationTarget for a GroupNotification for an Exercise in an Exam including the updated Problem Statement.
     *
     * @param exercise for which to create the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createExamExerciseTargetWithExerciseUpdate(Exercise exercise) {
        NotificationTarget target = new NotificationTarget(EXAMS_TEXT, exercise.getCourseViaExerciseGroupOrCourseMember().getId(), COURSES_TEXT);
        target.setProblemStatement(exercise.getProblemStatement());
        target.setExerciseId(exercise.getId());
        target.setExamId(exercise.getExamViaExerciseGroupOrCourseMember().getId());
        return target;
    }

    /**
     * Create a NotificationTarget for a GroupNotification for an Exercise.
     *
     * @param exercise for which to create the notification
     * @param message  to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createExerciseTarget(Exercise exercise, String message) {
        return new NotificationTarget(message, exercise.getId(), EXERCISES_TEXT, exercise.getCourseViaExerciseGroupOrCourseMember().getId(), COURSES_TEXT);
    }

    /**
     * Create a NotificationTarget for a SingleUserNotification for a successful data export creation
     *
     * @param dataExport the data export that was created
     * @param message    to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createDataExportCreatedTarget(DataExport dataExport, String message) {
        NotificationTarget notificationTarget = new NotificationTarget();
        notificationTarget.setEntity(message);
        notificationTarget.setIdentifier(dataExport.getId());
        notificationTarget.setMainPage(PRIVACY);
        return notificationTarget;
    }

    /**
     * Create a NotificationTarget for a SingleUserNotification for a failed data export creation
     *
     * @param message to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createDataExportFailedTarget(String message) {
        NotificationTarget notificationTarget = new NotificationTarget();
        notificationTarget.setEntity(message);
        notificationTarget.setMainPage(PRIVACY);
        return notificationTarget;
    }

    /**
     * Create a NotificationTarget for a GroupNotification for a duplicate test case.
     *
     * @param exercise with duplicated test cases
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createDuplicateTestCaseTarget(Exercise exercise) {
        return new NotificationTarget(DUPLICATE_TEST_CASE_TEXT, exercise.getId(), PROGRAMMING_EXERCISES_TEXT, exercise.getCourseViaExerciseGroupOrCourseMember().getId(),
                COURSE_MANAGEMENT_TEXT);
    }

    // LECTURE related targets

    /**
     * Create a NotificationTarget for a GroupNotification for a Lecture.
     *
     * @param lecture for which to create the notification
     * @param message to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createLectureTarget(Lecture lecture, String message) {
        return new NotificationTarget(message, lecture.getId(), LECTURES_TEXT, lecture.getCourse().getId(), COURSES_TEXT);
    }

    /**
     * Create the needed target for "AttachmentUpdated" notifications
     *
     * @param lecture where an attachment was updated
     * @return the final NotificationTarget
     */
    public static NotificationTarget createAttachmentUpdatedTarget(Lecture lecture) {
        return createLectureTarget(lecture, ATTACHMENT_UPDATED_TEXT);
    }

    // COURSE related targets

    /**
     * Create a NotificationTarget for a GroupNotification for a Course.
     *
     * @param course  for which to create the notification
     * @param message to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createCourseTarget(Course course, String message) {
        return new NotificationTarget(message, course.getId(), COURSES_TEXT, course.getId(), COURSES_TEXT);
    }

    // POST related targets

    /**
     * Create a NotificationTarget for "LecturePost" notifications
     *
     * @param post   which contains the needed lecture
     * @param course the post belongs to
     * @return the final NotificationTarget
     */
    public static NotificationTarget createLecturePostTarget(Post post, Course course) {
        NotificationTarget target = new NotificationTarget(post.getId(), course.getId());
        target.setLectureId(post.getLecture().getId());
        return target;
    }

    /**
     * Create a NotificationTarget for "ExercisePost" notifications
     *
     * @param post   which contains the needed exercise
     * @param course the post belongs to
     * @return the final NotificationTarget
     */
    public static NotificationTarget createExercisePostTarget(Post post, Course course) {
        NotificationTarget target = new NotificationTarget(post.getId(), course.getId());
        target.setExerciseId(post.getExercise().getId());
        return target;
    }

    /**
     * Create a NotificationTarget for "CoursePost" notifications
     *
     * @param post   course-wide post
     * @param course the post belongs to
     * @return the final NotificationTarget
     */
    public static NotificationTarget createCoursePostTarget(Post post, Course course) {
        return new NotificationTarget(post.getId(), course.getId());
    }

    // Plagiarism related targets

    /**
     * Create a NotificationTarget for plagiarism case related notifications
     *
     * @param plagiarismCaseId is the id of the PlagiarismCase
     * @param courseId         of the Course
     * @return the final NotificationTarget
     */
    public static NotificationTarget createPlagiarismCaseTarget(Long plagiarismCaseId, Long courseId) {
        return new NotificationTarget(PLAGIARISM_DETECTED_TEXT, plagiarismCaseId, PLAGIARISM_TEXT, courseId, COURSES_TEXT);
    }

    // Tutorial Group related targets

    /**
     * Create a NotificationTarget for notifications related to a Tutorial Group.
     *
     * @param tutorialGroup that is related to the notification
     * @param courseId      of the course to which the tutorial group belongs
     * @param isManagement  true if the notification should link to the tutorial group management page
     * @param isDetailPage  true if the notification should lik to the detail page of the tutorial group
     * @return the created NotificationTarget
     */
    public static NotificationTarget createTutorialGroupTarget(TutorialGroup tutorialGroup, Long courseId, boolean isManagement, boolean isDetailPage) {
        var notificationTarget = new NotificationTarget();
        if (isDetailPage) {
            notificationTarget.setIdentifier(tutorialGroup.getId());
        }
        notificationTarget.setEntity(isManagement ? TUTORIAL_GROUP_MANAGEMENT_TEXT : TUTORIAL_GROUPS_TEXT);
        notificationTarget.setCourseId(courseId);
        notificationTarget.setMainPage(isManagement ? COURSE_MANAGEMENT_TEXT : COURSES_TEXT);
        return notificationTarget;
    }

    // Conversation related targets

    /**
     * Create a NotificationTarget for notifications related to a new message in conversation.
     *
     * @param message  that is related to the notification
     * @param courseId of the course to which the conversation belongs
     * @return the created NotificationTarget
     */
    public static NotificationTarget createConversationMessageTarget(Post message, Long courseId) {
        var notificationTarget = new NotificationTarget(NEW_MESSAGE_TEXT, message.getId(), MESSAGE_TEXT, courseId, COURSES_TEXT);
        notificationTarget.setConversationId(message.getConversation().getId());
        return notificationTarget;
    }

    /**
     * Create a NotificationTarget for notifications related to a new conversation creation.
     *
     * @param conversation that is related to the notification
     * @param courseId     of the course to which the conversation belongs
     * @return the created NotificationTarget
     */
    public static NotificationTarget createConversationCreationTarget(Conversation conversation, Long courseId) {
        var notificationTarget = new NotificationTarget(CONVERSATION_CREATION_TEXT, conversation.getId(), CONVERSATION_TEXT, courseId, COURSES_TEXT);
        notificationTarget.setConversationId(conversation.getId());
        return notificationTarget;
    }

    /**
     * Create a NotificationTarget for notifications related to a new message reply in conversation.
     *
     * @param messageReply that is related to the notification
     * @param courseId     of the course to which the tutorial group belongs
     * @return the created NotificationTarget
     */
    public static NotificationTarget createMessageReplyTarget(AnswerPost messageReply, Long courseId) {
        var notificationTarget = new NotificationTarget(NEW_REPLY_TEXT, messageReply.getPost().getId(), MESSAGE_TEXT, courseId, COURSES_TEXT);
        notificationTarget.setConversationId(messageReply.getPost().getConversation().getId());
        return notificationTarget;
    }

    /**
     * Create a NotificationTarget for notifications related to conversation deletion.
     *
     * @param conversation that is related to the notification
     * @param courseId     of the course to which the tutorial group belongs
     * @return the created NotificationTarget
     */
    public static NotificationTarget createConversationDeletionTarget(Conversation conversation, Long courseId) {
        var notificationTarget = new NotificationTarget(CONVERSATION_DELETION_TEXT, conversation.getId(), CONVERSATION_TEXT, courseId, COURSES_TEXT);
        notificationTarget.setConversationId(conversation.getId());
        return notificationTarget;
    }

    /// URL/Link related methods

    /**
     * Extracts a viable URL from the provided notification and baseUrl
     *
     * @param notification which transient target property will be used for creating the URL
     * @param baseUrl      the prefix (depends on current set up (e.g. "http://localhost:9000/courses"))
     * @return viable URL to the notification related page
     */
    public static String extractNotificationUrl(Notification notification, String baseUrl) {
        NotificationTarget target = notification.getTargetTransient();
        if (target == null) {
            return "";
        }
        StringBuilder url = new StringBuilder(baseUrl);
        if (target.getMainPage() != null) {
            url.append("/").append(target.getMainPage());
        }
        if (target.getCourseId() != null) {
            url.append("/").append(target.getCourseId());
        }
        if (target.getEntity() != null) {
            url.append("/").append(target.getEntity());
        }
        if (target.getIdentifier() != null) {
            url.append("/").append(target.getIdentifier());
        }

        return url.toString();
    }

    /**
     * Extracts a viable URL from the provided notification that is based on a Post and baseUrl
     *
     * @param post    which information will be needed to create the URL
     * @param baseUrl the prefix (depends on current set up (e.g. "http://localhost:9000/courses"))
     * @return viable URL to the notification related page
     */
    public static String extractNotificationUrl(Post post, String baseUrl) {
        // e.g. http://localhost:8080/courses/1/discussion?searchText=%2382 for announcement post
        return baseUrl + "/courses/" + post.getCourse().getId() + "/discussion?searchText=%23" + post.getId();
    }
}
