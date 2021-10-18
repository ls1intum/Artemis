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

    // EXERCISE related targets

    /**
     * Get the needed target for "ExerciseCreated" notifications
     * @param exercise that was created
     * @return the final target property
     */
    public String getExerciseCreatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "exerciseCreated");
    }

    /**
     * Get the needed target for "ExerciseUpdated" notifications
     * @param exercise that was updated
     * @return the final target property
     */
    public String getExerciseUpdatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "exerciseUpdated");
    }

    /**
     * Create JSON representation for a GroupNotification for an ProgrammingExercise in an Exam or if duplicated test cases were detected.
     *
     * @param programmingExercise for which to create the notification.
     * @param message             to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getExamProgrammingExerciseOrTestCaseTarget(ProgrammingExercise programmingExercise, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", programmingExercise.getId());
        target.addProperty("entity", "programming-exercises");
        target.addProperty("course", programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty("mainPage", "course-management");
        return target.toString();
    }

    /**
     * Create JSON representation for a GroupNotification for an Exercise in an Exam including the updated Problem Statement.
     *
     * @param exercise for which to create the notification.
     * @return the stringified JSON of the target with the updated problem statement of exercise.
     */
    public String getExamExerciseTargetWithExerciseUpdate(Exercise exercise) {
        JsonObject target = new JsonObject();
        target.addProperty("problemStatement", exercise.getProblemStatement());
        target.addProperty("exercise", exercise.getId());
        target.addProperty("exam", exercise.getExamViaExerciseGroupOrCourseMember().getId());
        target.addProperty("entity", "exams");
        target.addProperty("course", exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    /**
     * Create JSON representation for a GroupNotification for an Exercise.
     *
     * @param exercise for which to create the notification.
     * @param message to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getExerciseTarget(Exercise exercise, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", exercise.getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    // LECTURE related targets

    /**
     * Create JSON representation for a GroupNotification for a Lecture.
     *
     * @param lecture for which to create the notification.
     * @param message to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getLectureTarget(Lecture lecture, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", lecture.getId());
        target.addProperty("entity", "lectures");
        target.addProperty("course", lecture.getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    /**
     * Get the needed target for "AttachmentUpdated" notifications
     * @param lecture where an attachment was updated
     * @return the final target property
     */
    public String getAttachmentUpdatedTarget(Lecture lecture) {
        return getLectureTarget(lecture, "attachmentUpdated");
    }

    // COURSE related targets

    /**
     * Create JSON representation for a GroupNotification for a Course.
     *
     * @param course for which to create the notification.
     * @param message to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getCourseTarget(Course course, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", course.getId());
        target.addProperty("entity", "courses");
        target.addProperty("course", course.getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    // POST related targets

    /**
     * Get the needed target for "LecturePost" notifications
     * @param post which contains the needed lecture
     * @return the final target property
     */
    public String getLecturePostTarget(Post post) {
        JsonObject target = new JsonObject();
        target.addProperty("id", post.getId());
        target.addProperty("lectureId", post.getLecture().getId());
        target.addProperty("course", post.getCourse().getId());
        return target.toString();
    }

    /**
     * Get the needed target for "ExercisePost" notifications
     * @param post which contains the needed exercise
     * @return the final target property
     */
    public String getExercisePostTarget(Post post) {
        JsonObject target = new JsonObject();
        target.addProperty("id", post.getId());
        target.addProperty("exerciseId", post.getExercise().getId());
        target.addProperty("course", post.getCourse().getId());
        return target.toString();
    }

    /**
     * Get the needed target for "CoursePost" notifications
     * @param post which contains the needed course
     * @return the final target property
     */
    public String getCoursePostTarget(Post post, Course course) {
        JsonObject target = new JsonObject();
        target.addProperty("id", post.getId());
        target.addProperty("course", course.getId());
        return target.toString();
    }
}
