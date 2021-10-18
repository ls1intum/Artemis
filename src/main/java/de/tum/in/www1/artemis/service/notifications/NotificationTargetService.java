package de.tum.in.www1.artemis.service.notifications;

import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.metis.Post;

@Service
public class NotificationTargetService {

    // shared constants
    private final String MESSAGE_TEXT = "message";

    private final String ID_TEXT = "id";

    private final String ENTITY_TEXT = "entity";

    private final String COURSE_TEXT = "course";

    private final String COURSES_TEXT = "courses";

    private final String MAIN_PAGE_TEXT = "mainPage";

    private final String PROGRAMMING_EXERCISES_TEXT = "programming-exercises";

    private final String COURSE_MANAGEMENT_TEXT = "course-management";

    private final String PROBLEM_STATEMENT_TEXT = "problemStatement";

    private final String EXERCISES_TEXT = "exercises";

    private final String EXERCISE_ID_TEXT = "exerciseId";

    private final String EXAM_TEXT = "exam";

    private final String EXAMS_TEXT = "exams";

    private final String LECTURES_TEXT = "lectures";

    private final String LECTURE_ID_TEXT = "lectureId";

    private final String ATTACHMENT_UPDATED_TEXT = "attachmentUpdated";

    private final String EXERCISE_CREATED_TEXT = "exerciseCreated";

    private final String EXERCISE_UPDATED_TEXT = "exerciseUpdated";

    // EXERCISE related targets

    /**
     * Get the needed target for "ExerciseCreated" notifications
     * @param exercise that was created
     * @return the final target property
     */
    public String getExerciseCreatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, EXERCISE_CREATED_TEXT);
    }

    /**
     * Get the needed target for "ExerciseUpdated" notifications
     * @param exercise that was updated
     * @return the final target property
     */
    public String getExerciseUpdatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
    }

    /**
     * Create JSON representation for a GroupNotification for an ProgrammingExercise in an Exam or if duplicated test cases were detected.
     *
     * @param programmingExercise for which to create the notification
     * @param message             to use for the notification
     * @return the stringified JSON of the target
     */
    public String getExamProgrammingExerciseOrTestCaseTarget(ProgrammingExercise programmingExercise, String message) {
        JsonObject target = new JsonObject();
        target.addProperty(MESSAGE_TEXT, message);
        target.addProperty(ID_TEXT, programmingExercise.getId());
        target.addProperty(ENTITY_TEXT, PROGRAMMING_EXERCISES_TEXT);
        target.addProperty(COURSE_TEXT, programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty(MAIN_PAGE_TEXT, COURSE_MANAGEMENT_TEXT);
        return target.toString();
    }

    /**
     * Create JSON representation for a GroupNotification for an Exercise in an Exam including the updated Problem Statement.
     *
     * @param exercise for which to create the notification
     * @return the stringified JSON of the target with the updated problem statement of exercise
     */
    public String getExamExerciseTargetWithExerciseUpdate(Exercise exercise) {
        JsonObject target = new JsonObject();
        target.addProperty(PROBLEM_STATEMENT_TEXT, exercise.getProblemStatement());
        target.addProperty(EXERCISES_TEXT, exercise.getId());
        target.addProperty(EXAM_TEXT, exercise.getExamViaExerciseGroupOrCourseMember().getId());
        target.addProperty(ENTITY_TEXT, EXAMS_TEXT);
        target.addProperty(COURSE_TEXT, exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty(MAIN_PAGE_TEXT, COURSES_TEXT);
        return target.toString();
    }

    /**
     * Create JSON representation for a GroupNotification for an Exercise.
     *
     * @param exercise for which to create the notification
     * @param message to use for the notification
     * @return the stringified JSON of the target
     */
    public String getExerciseTarget(Exercise exercise, String message) {
        JsonObject target = new JsonObject();
        target.addProperty(MESSAGE_TEXT, message);
        target.addProperty(ID_TEXT, exercise.getId());
        target.addProperty(ENTITY_TEXT, EXERCISES_TEXT);
        target.addProperty(COURSE_TEXT, exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty(MAIN_PAGE_TEXT, COURSES_TEXT);
        return target.toString();
    }

    // LECTURE related targets

    /**
     * Create JSON representation for a GroupNotification for a Lecture.
     *
     * @param lecture for which to create the notification
     * @param message to use for the notification
     * @return the stringified JSON of the target
     */
    public String getLectureTarget(Lecture lecture, String message) {
        JsonObject target = new JsonObject();
        target.addProperty(MESSAGE_TEXT, message);
        target.addProperty(ID_TEXT, lecture.getId());
        target.addProperty(ENTITY_TEXT, LECTURES_TEXT);
        target.addProperty(COURSE_TEXT, lecture.getCourse().getId());
        target.addProperty(MAIN_PAGE_TEXT, COURSES_TEXT);
        return target.toString();
    }

    /**
     * Get the needed target for "AttachmentUpdated" notifications
     * @param lecture where an attachment was updated
     * @return the final target property
     */
    public String getAttachmentUpdatedTarget(Lecture lecture) {
        return getLectureTarget(lecture, ATTACHMENT_UPDATED_TEXT);
    }

    // COURSE related targets

    /**
     * Create JSON representation for a GroupNotification for a Course.
     *
     * @param course for which to create the notification
     * @param message to use for the notification
     * @return the stringified JSON of the target
     */
    public String getCourseTarget(Course course, String message) {
        JsonObject target = new JsonObject();
        target.addProperty(MESSAGE_TEXT, message);
        target.addProperty(ID_TEXT, course.getId());
        target.addProperty(ENTITY_TEXT, COURSES_TEXT);
        target.addProperty(COURSE_TEXT, course.getId());
        target.addProperty(MAIN_PAGE_TEXT, COURSES_TEXT);
        return target.toString();
    }

    // POST related targets

    /**
     * Get the needed target for "LecturePost" notifications
     * @param post which contains the needed lecture
     * @param course the post belongs to
     * @return the final target property
     */
    public String getLecturePostTarget(Post post, Course course) {
        JsonObject target = new JsonObject();
        target.addProperty(ID_TEXT, post.getId());
        target.addProperty(LECTURE_ID_TEXT, course.getId());
        target.addProperty(COURSE_TEXT, post.getCourse().getId());
        return target.toString();
    }

    /**
     * Get the needed target for "ExercisePost" notifications
     * @param post which contains the needed exercise
     * @param course the post belongs to
     * @return the final target property
     */
    public String getExercisePostTarget(Post post, Course course) {
        JsonObject target = new JsonObject();
        target.addProperty(ID_TEXT, post.getId());
        target.addProperty(EXERCISE_ID_TEXT, course.getId());
        target.addProperty(COURSE_TEXT, post.getCourse().getId());
        return target.toString();
    }

    /**
     * Get the needed target for "CoursePost" notifications
     * @param post course-wide post
     * @param course the post belongs to
     * @return the final target property
     */
    public String getCoursePostTarget(Post post, Course course) {
        JsonObject target = new JsonObject();
        target.addProperty(ID_TEXT, post.getId());
        target.addProperty(COURSE_TEXT, course.getId());
        return target.toString();
    }
}
