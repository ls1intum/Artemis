package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;

/**
 * What deciding whether a student's exam working period is over needs to know about their student exam.
 * <p>
 * Read instead of the student exam entity, whose {@code exam} is a {@code @ManyToOne} and therefore eager, as is that
 * exam's course and the course's configuration. Loading the entity to read two booleans issued four further selects,
 * and this runs on the submission path of every exam exercise. The caller already holds the exam.
 *
 * @param submitted   whether the student has handed the exam in
 * @param testRun     whether the student exam is an instructor test run
 * @param started     whether the student has started the exam
 * @param startedDate when the student started, only relevant for test exams
 * @param workingTime the working time in seconds
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamWorkingPeriodDTO(Boolean submitted, Boolean testRun, Boolean started, ZonedDateTime startedDate, Integer workingTime) {

    /**
     * Whether the working period is over: the student has handed in, or their individual end date has passed.
     *
     * @param exam the exam the student exam belongs to
     * @return true when the student can no longer work on the exam
     */
    public boolean isWorkingPeriodOver(Exam exam) {
        return Boolean.TRUE.equals(submitted) || Boolean.TRUE.equals(StudentExam.isEnded(exam, testRun, started, startedDate, workingTime));
    }
}
