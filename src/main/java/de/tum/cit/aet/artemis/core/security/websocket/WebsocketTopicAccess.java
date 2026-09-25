package de.tum.cit.aet.artemis.core.security.websocket;

import java.util.List;
import java.util.function.BiPredicate;

import de.tum.cit.aet.artemis.core.domain.CourseRole;

/**
 * Who may subscribe to a {@link WebsocketTopic}, the websocket counterpart of the {@code @Enforce...} annotations of a REST endpoint.
 * <p>
 * The course, exercise and lecture rules read the id from a variable of the topic template, e.g. {@code atLeastStudentInCourse("courseId")} for
 * {@code /topic/communication/courses/{courseId}}, and admit an administrator with active elevation as well. {@link #ownUser(String)} and custom rules do not admit
 * administrators on their own, because the topic belongs to one person; a custom rule can still ask {@link WebsocketSubscription#hasAdministratorAccess()}.
 * <p>
 * Pick the narrowest rule that fits the payload. A rule that needs data of the module, such as the owner of a student exam, is a {@link #custom custom} check implemented
 * as a method of the module's {@link WebsocketTopicProvider}.
 */
public sealed interface WebsocketTopicAccess {

    /**
     * @return the names of the template variables this rule reads, validated against the template when the topic is declared
     */
    default List<String> variableNames() {
        return List.of();
    }

    /**
     * Any logged-in user. Only for data that every user may see, such as system notifications or feature toggles.
     */
    record AnyAuthenticatedUser() implements WebsocketTopicAccess {
    }

    /**
     * An administrator with active elevation (see {@code ElevatedAccessService}).
     */
    record ElevatedAdministrator() implements WebsocketTopicAccess {
    }

    /**
     * Only the user whose id is in the destination. No administrator override.
     *
     * @param userIdVariable the template variable holding the user id
     */
    record OwnUser(String userIdVariable) implements WebsocketTopicAccess {

        @Override
        public List<String> variableNames() {
            return List.of(userIdVariable);
        }
    }

    /**
     * At least the given role in the course, or an administrator.
     *
     * @param minimum          the minimum course role
     * @param courseIdVariable the template variable holding the course id
     */
    record AtLeastRoleInCourse(CourseRole minimum, String courseIdVariable) implements WebsocketTopicAccess {

        @Override
        public List<String> variableNames() {
            return List.of(courseIdVariable);
        }
    }

    /**
     * At least the given role in the course of the exercise, or an administrator.
     *
     * @param minimum                 the minimum course role for a course exercise
     * @param minimumForExamExercises the minimum course role for an exam exercise
     * @param exerciseIdVariable      the template variable holding the exercise id
     */
    record AtLeastRoleInExercise(CourseRole minimum, CourseRole minimumForExamExercises, String exerciseIdVariable) implements WebsocketTopicAccess {

        @Override
        public List<String> variableNames() {
            return List.of(exerciseIdVariable);
        }
    }

    /**
     * At least the given role in the course of the lecture, or an administrator.
     *
     * @param minimum           the minimum course role
     * @param lectureIdVariable the template variable holding the lecture id
     */
    record AtLeastRoleInLecture(CourseRole minimum, String lectureIdVariable) implements WebsocketTopicAccess {

        @Override
        public List<String> variableNames() {
            return List.of(lectureIdVariable);
        }
    }

    /**
     * A check implemented by the provider that declares the topic, for rules that need data of the module.
     *
     * @param provider the provider class; the check is invoked on the provider bean that declares the topic
     * @param check    the check, typically a method reference such as {@code ExamWebsocketTopics::isOwnerOfStudentExam}
     * @param <P>      the provider type
     */
    record Custom<P extends WebsocketTopicProvider>(Class<P> provider, BiPredicate<P, WebsocketSubscription> check) implements WebsocketTopicAccess {

        boolean isAllowed(WebsocketTopicProvider declaringProvider, WebsocketSubscription subscription) {
            return check.test(provider.cast(declaringProvider), subscription);
        }

        @Override
        public String toString() {
            return "Custom[" + provider.getSimpleName() + "]";
        }
    }

    /**
     * @return a rule admitting any logged-in user
     */
    static WebsocketTopicAccess anyAuthenticatedUser() {
        return new AnyAuthenticatedUser();
    }

    /**
     * @return a rule admitting administrators with active elevation
     */
    static WebsocketTopicAccess administrator() {
        return new ElevatedAdministrator();
    }

    /**
     * @param userIdVariable the template variable holding the user id
     * @return a rule admitting only the user whose id is in the destination
     */
    static WebsocketTopicAccess ownUser(String userIdVariable) {
        return new OwnUser(userIdVariable);
    }

    /**
     * @param courseIdVariable the template variable holding the course id
     * @return a rule admitting students, tutors, editors and instructors of the course
     */
    static WebsocketTopicAccess atLeastStudentInCourse(String courseIdVariable) {
        return new AtLeastRoleInCourse(CourseRole.STUDENT, courseIdVariable);
    }

    /**
     * @param courseIdVariable the template variable holding the course id
     * @return a rule admitting tutors, editors and instructors of the course
     */
    static WebsocketTopicAccess atLeastTutorInCourse(String courseIdVariable) {
        return new AtLeastRoleInCourse(CourseRole.TEACHING_ASSISTANT, courseIdVariable);
    }

    /**
     * @param courseIdVariable the template variable holding the course id
     * @return a rule admitting editors and instructors of the course
     */
    static WebsocketTopicAccess atLeastEditorInCourse(String courseIdVariable) {
        return new AtLeastRoleInCourse(CourseRole.EDITOR, courseIdVariable);
    }

    /**
     * @param courseIdVariable the template variable holding the course id
     * @return a rule admitting instructors of the course
     */
    static WebsocketTopicAccess atLeastInstructorInCourse(String courseIdVariable) {
        return new AtLeastRoleInCourse(CourseRole.INSTRUCTOR, courseIdVariable);
    }

    /**
     * @param exerciseIdVariable the template variable holding the exercise id
     * @return a rule admitting tutors, editors and instructors of the exercise's course
     */
    static WebsocketTopicAccess atLeastTutorInExercise(String exerciseIdVariable) {
        return new AtLeastRoleInExercise(CourseRole.TEACHING_ASSISTANT, CourseRole.TEACHING_ASSISTANT, exerciseIdVariable);
    }

    /**
     * For topics that carry the work of all students of an exercise, such as results with their feedback: tutors see them for course exercises, but only instructors for
     * exam exercises, which tutors assess after the exam through the assessment dashboard.
     *
     * @param exerciseIdVariable the template variable holding the exercise id
     * @return a rule admitting tutors of a course exercise, and instructors of an exam exercise
     */
    static WebsocketTopicAccess atLeastTutorInExerciseAndInstructorInExamExercise(String exerciseIdVariable) {
        return new AtLeastRoleInExercise(CourseRole.TEACHING_ASSISTANT, CourseRole.INSTRUCTOR, exerciseIdVariable);
    }

    /**
     * @param exerciseIdVariable the template variable holding the exercise id
     * @return a rule admitting editors and instructors of the exercise's course
     */
    static WebsocketTopicAccess atLeastEditorInExercise(String exerciseIdVariable) {
        return new AtLeastRoleInExercise(CourseRole.EDITOR, CourseRole.EDITOR, exerciseIdVariable);
    }

    /**
     * @param exerciseIdVariable the template variable holding the exercise id
     * @return a rule admitting instructors of the exercise's course
     */
    static WebsocketTopicAccess atLeastInstructorInExercise(String exerciseIdVariable) {
        return new AtLeastRoleInExercise(CourseRole.INSTRUCTOR, CourseRole.INSTRUCTOR, exerciseIdVariable);
    }

    /**
     * @param lectureIdVariable the template variable holding the lecture id
     * @return a rule admitting editors and instructors of the lecture's course
     */
    static WebsocketTopicAccess atLeastEditorInLecture(String lectureIdVariable) {
        return new AtLeastRoleInLecture(CourseRole.EDITOR, lectureIdVariable);
    }

    /**
     * A check that needs data of the module. Implement it as a method of the provider class and pass it as a method reference:
     *
     * <pre>
     *
     * public static final WebsocketTopic STUDENT_EXAM_EVENTS = WebsocketTopic.of("/topic/exam-participation/studentExam/{studentExamId}/events",
     *         WebsocketTopicAccess.custom(ExamWebsocketTopics.class, ExamWebsocketTopics::isOwnerOfStudentExam));
     * </pre>
     *
     * @param provider the provider class that declares the topic and implements the check
     * @param check    the check
     * @param <P>      the provider type
     * @return a rule delegating to the check
     */
    static <P extends WebsocketTopicProvider> WebsocketTopicAccess custom(Class<P> provider, BiPredicate<P, WebsocketSubscription> check) {
        return new Custom<>(provider, check);
    }
}
