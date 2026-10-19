package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;

/**
 * The two values a student exam contributes to its individual end date. Read on its own where a caller needs nothing
 * else from the student exam, so the whole row does not have to be loaded.
 *
 * @param workingTime the working time of the student exam in seconds, null if none is set
 * @param startedDate when the student started the exam, only relevant for test exams
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamWorkingTimeDTO(Integer workingTime, ZonedDateTime startedDate) {

    /**
     * Delegates to {@link StudentExam#individualEndDate} so this projection and the entity cannot drift apart.
     *
     * @param exam the exam the student exam belongs to
     * @return the individual end date, or null if no working time is set
     */
    public ZonedDateTime individualEndDate(Exam exam) {
        if (workingTime == null) {
            return null;
        }
        return StudentExam.individualEndDate(exam, startedDate, workingTime);
    }
}
