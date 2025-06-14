package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamCheckDTO(long studentExamId, boolean submitted, boolean started, ZonedDateTime studentExamStartedDate, int workingTime, boolean isTestRun,
        ZonedDateTime examStartDate, boolean isTestExam) {

    /**
     * check if the individual student exam has ended (based on the working time)
     * For test exams, we cannot use exam.startTime, but need to use the student.startedDate. If this is not yet set,
     * the studentExams has not yet started and therefore cannot be ended.
     *
     * @return true if the exam has finished, otherwise false
     */
    @JsonIgnore
    public boolean isEnded() {
        if (isTestRun) {
            return false;
        }
        if (isTestExam && !started && studentExamStartedDate == null) {
            return false;
        }
        return ZonedDateTime.now().isAfter(getIndividualEndDate());
    }

    @JsonIgnore
    public ZonedDateTime getIndividualEndDate() {
        if (isTestExam) {
            if (studentExamStartedDate == null) {
                return null;
            }
            return studentExamStartedDate.plusSeconds(workingTime);
        }
        return examStartDate.plusSeconds(workingTime);
    }
}
