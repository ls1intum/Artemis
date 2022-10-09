package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.metis.Post;
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

    public static final String ATTACHMENT_UPDATED_TEXT = "attachmentUpdated";

    public static final String EXERCISE_RELEASED_TEXT = "exerciseReleased";

    public static final String EXERCISE_UPDATED_TEXT = "exerciseUpdated";

    public static final String DUPLICATE_TEST_CASE_TEXT = "duplicateTestCase";

    public static final String COURSE_ARCHIVE_UPDATED_TEXT = "courseArchiveUpdated";

    public static final String EXAM_ARCHIVE_UPDATED_TEXT = "examArchiveUpdated";

    public static final String PLAGIARISM_TEXT = "plagiarism-cases";

    public static final String PLAGIARISM_DETECTED_TEXT = "plagiarismDetected";

    // EXERCISE related targets

    /**
     * Create the needed target for "ExerciseReleased" notifications
     * @param exercise that was released
     * @return the final target property
     */
    public static NotificationTarget createExerciseReleasedTarget(Exercise exercise) {
        return createExerciseTarget(exercise, EXERCISE_RELEASED_TEXT);
    }

    /**
     * Create the needed target for "ExerciseUpdated" notifications
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
     * @param message to use for the notification
     * @return the final NotificationTarget for this case
     */
    public static NotificationTarget createExerciseTarget(Exercise exercise, String message) {
        return new NotificationTarget(message, exercise.getId(), EXERCISES_TEXT, exercise.getCourseViaExerciseGroupOrCourseMember().getId(), COURSES_TEXT);
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
     * @param course for which to create the notification
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
     * @param post which contains the needed lecture
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
     * @param post which contains the needed exercise
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
     * @param post course-wide post
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
    public static NotificationTarget createTutorialGroupTarget(TutorialGroup tutorialGroup, Long courseId, boolean isManagement) {
        var notificationTarget = new NotificationTarget();
        notificationTarget.setIdentifier(tutorialGroup.getId());
        notificationTarget.setEntity(isManagement ? TUTORIAL_GROUP_MANAGEMENT_TEXT : TUTORIAL_GROUPS_TEXT);
        notificationTarget.setCourseId(courseId);
        notificationTarget.setMainPage(isManagement ? COURSE_MANAGEMENT_TEXT : COURSES_TEXT);
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
        return baseUrl + "/" + target.getMainPage() + "/" + target.getCourseId() + "/" + target.getEntity() + "/" + target.getIdentifier();
    }

    /**
     * Extracts a viable URL from the provided notification that is based on a Post and baseUrl
     *
     * @param post which information will be needed to create the URL
     * @param baseUrl the prefix (depends on current set up (e.g. "http://localhost:9000/courses"))
     * @return viable URL to the notification related page
     */
    public static String extractNotificationUrl(Post post, String baseUrl) {
        // e.g. http://localhost:8080/courses/1/discussion?searchText=%2382 for announcement post
        return baseUrl + "/courses/" + post.getCourse().getId() + "/discussion?searchText=%23" + post.getId();
    }
}
