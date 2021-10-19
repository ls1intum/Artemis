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
    private static final String messageText = "message";

    private static final String idText = "id";

    private static final String entityText = "entity";

    private static final String courseText = "course";

    private static final String coursesText = "courses";

    private static final String mainPageText = "mainPage";

    private static final String programmingExercisesText = "programming-exercises";

    private static final String courseManagementText = "course-management";

    private static final String problemStatementText = "problemStatement";

    private static final String exerciseText = "exercise";

    private static final String exercisesText = "exercises";

    private static final String exerciseIdText = "exerciseId";

    private static final String examText = "exam";

    private static final String examsText = "exams";

    private static final String lecturesText = "lectures";

    private static final String lectureIdText = "lectureId";

    private static final String attachmentUpdatedText = "attachmentUpdated";

    private static final String exerciseCreatedText = "exerciseCreated";

    private static final String exerciseUpdatedText = "exerciseUpdated";

    // EXERCISE related targets

    /**
     * Get the needed target for "ExerciseCreated" notifications
     * @param exercise that was created
     * @return the final target property
     */
    public String getExerciseCreatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, exerciseCreatedText);
    }

    /**
     * Get the needed target for "ExerciseUpdated" notifications
     * @param exercise that was updated
     * @return the final target property
     */
    public String getExerciseUpdatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, exerciseUpdatedText);
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
        target.addProperty(messageText, message);
        target.addProperty(idText, programmingExercise.getId());
        target.addProperty(entityText, programmingExercisesText);
        target.addProperty(courseText, programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty(mainPageText, courseManagementText);
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
        target.addProperty(problemStatementText, exercise.getProblemStatement());
        target.addProperty(exerciseText, exercise.getId());
        target.addProperty(examText, exercise.getExamViaExerciseGroupOrCourseMember().getId());
        target.addProperty(entityText, examsText);
        target.addProperty(courseText, exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty(mainPageText, coursesText);
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
        target.addProperty(messageText, message);
        target.addProperty(idText, exercise.getId());
        target.addProperty(entityText, exercisesText);
        target.addProperty(courseText, exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty(mainPageText, coursesText);
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
        target.addProperty(messageText, message);
        target.addProperty(idText, lecture.getId());
        target.addProperty(entityText, lecturesText);
        target.addProperty(courseText, lecture.getCourse().getId());
        target.addProperty(mainPageText, coursesText);
        return target.toString();
    }

    /**
     * Get the needed target for "AttachmentUpdated" notifications
     * @param lecture where an attachment was updated
     * @return the final target property
     */
    public String getAttachmentUpdatedTarget(Lecture lecture) {
        return getLectureTarget(lecture, attachmentUpdatedText);
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
        target.addProperty(messageText, message);
        target.addProperty(idText, course.getId());
        target.addProperty(entityText, coursesText);
        target.addProperty(courseText, course.getId());
        target.addProperty(mainPageText, coursesText);
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
        target.addProperty(idText, post.getId());
        target.addProperty(lectureIdText, post.getLecture().getId());
        target.addProperty(courseText, course.getId());
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
        target.addProperty(idText, post.getId());
        target.addProperty(exerciseIdText, post.getExercise().getId());
        target.addProperty(courseText, course.getId());
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
        target.addProperty(idText, post.getId());
        target.addProperty(courseText, course.getId());
        return target.toString();
    }
}
