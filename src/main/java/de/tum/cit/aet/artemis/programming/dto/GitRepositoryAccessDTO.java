package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Everything the git request path needs to decide whether a user may read or write a repository.
 * <p>
 * Every clone, fetch and push resolves its exercise before anything else, twice per git operation in practice: once
 * for the handshake and once for the transfer. Reading the exercise entity for that pulled its course in as well, and
 * for an exam exercise the course twice over, because it is reachable both directly and through the exercise group's
 * exam - roughly a hundred columns to reach the handful of values below.
 * <p>
 * The course is represented by its id alone because that is all the role checks read: they answer from the user's
 * course roles and otherwise from a membership query keyed by that id.
 *
 * @param exerciseId      the exercise behind the project key
 * @param courseId        the course the exercise belongs to, directly or through its exam
 * @param mode            whether participations are per team or per student
 * @param allowOfflineIde whether students may use git outside the online editor, {@code null} when never set
 * @param startDate       when participation opens, for a course exercise
 * @param releaseDate     the fallback for {@code startDate}, for a course exercise
 * @param dueDate         when submissions close, for a course exercise
 * @param examId          the exam, or {@code null} for a course exercise
 * @param examStartDate   when the exam starts, or {@code null} for a course exercise
 * @param testExam        whether the exam may be taken repeatedly, {@code null} for a course exercise, since the
 *                            exam side of the join is then absent
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitRepositoryAccessDTO(long exerciseId, long courseId, ExerciseMode mode, @Nullable Boolean allowOfflineIde, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime dueDate, @Nullable Long examId, @Nullable ZonedDateTime examStartDate, @Nullable Boolean testExam) {

    /**
     * Builds the projection from an exercise that is already loaded.
     * <p>
     * For the ssh path, which holds the entity for other reasons and would gain nothing from reading it again.
     *
     * @param exercise the loaded exercise, with its course and, for an exam exercise, its exercise group and exam
     * @return the same values the query projects
     * @throws IllegalStateException if no course is reachable from the exercise, which means the caller did not load the graph this projection needs
     */
    public static GitRepositoryAccessDTO of(ProgrammingExercise exercise) {
        Exam exam = exercise.isExamExercise() ? exercise.getExerciseGroup().getExam() : null;
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            // The course id is the whole point of this projection - every role check downstream is keyed by it - so an exercise that cannot produce one is a caller error
            // rather than a case to represent. Name the exercise; the alternative is a NullPointerException raised deep inside a repository handshake.
            throw new IllegalStateException("Cannot build the repository access projection for exercise " + exercise.getId() + ": no course is reachable from it.");
        }
        return new GitRepositoryAccessDTO(exercise.getId(), course.getId(), exercise.getMode(), exercise.isAllowOfflineIde(), exercise.getStartDate(), exercise.getReleaseDate(),
                exercise.getDueDate(), exam == null ? null : exam.getId(), exam == null ? null : exam.getStartDate(), exam == null ? null : exam.isTestExam());
    }

    /**
     * Mirrors {@code Exercise#isTestExamExercise()}: only an exam exercise can be a test exam.
     *
     * @return whether the exercise belongs to a test exam
     */
    public boolean isTestExamExercise() {
        return Boolean.TRUE.equals(testExam);
    }

    /**
     * Mirrors the entity check: only an explicit {@code false} forbids it, an unset value does not.
     *
     * @return whether offline git use is explicitly forbidden
     */
    public boolean offlineIdeForbidden() {
        return Boolean.FALSE.equals(allowOfflineIde);
    }

    /**
     * @return whether participations belong to teams rather than to individual students
     */
    public boolean isTeamMode() {
        return mode == ExerciseMode.TEAM;
    }

    /**
     * @return whether the exercise belongs to an exam rather than directly to a course
     */
    public boolean isExamExercise() {
        return examId != null;
    }

    /**
     * The moment participation opens, mirroring {@code Exercise#getParticipationStartDate()}: an exam exercise opens
     * when its exam starts, a course exercise when it starts or, failing that, when it is released.
     *
     * @return the moment participation opens, or {@code null} if it is open from the start
     */
    @Nullable
    public ZonedDateTime participationStartDate() {
        if (isExamExercise()) {
            return examStartDate;
        }
        return startDate != null ? startDate : releaseDate;
    }
}
