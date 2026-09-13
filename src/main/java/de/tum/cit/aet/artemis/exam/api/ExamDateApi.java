package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamDateApi extends AbstractExamApi {

    private final ExamDateService examDateService;

    public ExamDateApi(ExamDateService examDateService) {
        this.examDateService = examDateService;
    }

    public boolean isExamWithGracePeriodOver(Exam exam) {
        return examDateService.isExamWithGracePeriodOver(exam);
    }

    public boolean isIndividualExerciseWorkingPeriodOver(Exam exam, StudentParticipation studentParticipation) {
        return examDateService.isIndividualExerciseWorkingPeriodOver(exam, studentParticipation);
    }

    /**
     * Whether the student's working period for an exam exercise is over, for a caller holding a projection.
     *
     * @param exam            the exam the exercise belongs to
     * @param testRun         whether the participation is an instructor test run
     * @param participantId   the id of the student the participation belongs to
     * @param participationId the id of the participation
     * @return true if the working period is over, false otherwise
     */
    public boolean isIndividualExerciseWorkingPeriodOver(Exam exam, boolean testRun, long participantId, long participationId) {
        return examDateService.isIndividualExerciseWorkingPeriodOver(exam, testRun, participantId, participationId);
    }

    public ZonedDateTime getLatestIndividualExamEndDate(Long examId) {
        return examDateService.getLatestIndividualExamEndDate(examId);
    }

    public ZonedDateTime getLatestIndividualExamEndDate(Exam exam) {
        return examDateService.getLatestIndividualExamEndDate(exam);
    }
}
