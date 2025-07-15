package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@ConditionalOnProperty(name = "artemis.exam.enabled", havingValue = "true")
@Controller
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

    public ZonedDateTime getLatestIndividualExamEndDate(Long examId) {
        return examDateService.getLatestIndividualExamEndDate(examId);
    }

    public ZonedDateTime getLatestIndividualExamEndDate(Exam exam) {
        return examDateService.getLatestIndividualExamEndDate(exam);
    }
}
