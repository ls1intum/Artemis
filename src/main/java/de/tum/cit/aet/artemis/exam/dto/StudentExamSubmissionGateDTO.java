package de.tum.cit.aet.artemis.exam.dto;

import java.time.Instant;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.StudentExam;

/**
 * Everything the submission gate in {@code ExamSubmissionService} needs to know about a student exam, read in one query
 * instead of loading the student exam together with its exercises.
 * <p>
 * The exercise-membership check is answered by the database rather than by iterating an in-memory collection: this gate
 * runs on every autosave of every student, and the alternative is fetching the whole exercise list (a polymorphic join
 * over the exercise hierarchy) only to test whether one id is in it.
 *
 * @param studentExamId   the id of the student exam
 * @param submitted       whether the student exam has already been handed in
 * @param submissionDate  when the student exam was handed in, if it was
 * @param workingTime     the working time of the student exam in seconds, null if none is set
 * @param startedDate     when the student started the exam, only relevant for test exams
 * @param createdDate     when the student exam was created, used to pick the latest attempt of a test exam
 * @param exerciseMatches how many of the student exam's exercises have the requested id, so a value greater than zero
 *                            means the exercise belongs to this student exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamSubmissionGateDTO(long studentExamId, Boolean submitted, ZonedDateTime submissionDate, Integer workingTime, ZonedDateTime startedDate, Instant createdDate,
        long exerciseMatches) {

    /**
     * @return true if the requested exercise belongs to this student exam
     */
    public boolean containsRequestedExercise() {
        return exerciseMatches > 0;
    }

    /**
     * @return true if the student exam has already been handed in
     */
    public boolean isHandedIn() {
        return Boolean.TRUE.equals(submitted) || submissionDate != null;
    }

    /**
     * @return true if this student exam has an individual working time
     */
    public boolean hasWorkingTime() {
        return workingTime != null && workingTime > 0;
    }

    /**
     * Delegates to {@link StudentExam#individualEndDate} so this projection and the entity cannot drift apart. Only
     * meaningful when {@link #hasWorkingTime()} is true.
     *
     * @param examSchedule    the dates of the exam the student exam belongs to
     * @param withGracePeriod whether the exam's grace period should be added
     * @return the individual end date for this student exam
     */
    public ZonedDateTime individualEndDate(ExamScheduleDTO examSchedule, boolean withGracePeriod) {
        if (withGracePeriod) {
            return StudentExam.individualEndDateWithGracePeriod(examSchedule.testExam(), examSchedule.startDate(), examSchedule.gracePeriod(), startedDate, workingTime);
        }
        return StudentExam.individualEndDate(examSchedule.testExam(), examSchedule.startDate(), startedDate, workingTime);
    }
}
