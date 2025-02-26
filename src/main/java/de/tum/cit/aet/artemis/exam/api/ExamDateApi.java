package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Profile(PROFILE_CORE)
@Controller
public class ExamDateApi extends AbstractExamApi {

    private final ExamDateService examDateService;

    public ExamDateApi(ExamDateService examDateService) {
        this.examDateService = examDateService;
    }

    public boolean isExamWithGracePeriodOver(Exam exam) {
        return examDateService.isExamWithGracePeriodOver(exam);
    }

    public boolean isExerciseWorkingPeriodOver(Exercise exercise, StudentParticipation studentParticipation) {
        return examDateService.isExerciseWorkingPeriodOver(exercise, studentParticipation);
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

    public ZonedDateTime getLatestIndividualExamEndDateWithGracePeriod(Exam exam) {
        return examDateService.getLatestIndividualExamEndDateWithGracePeriod(exam);
    }

    public ZonedDateTime getExamProgrammingExerciseUnlockDate(ProgrammingExercise exercise) {
        return ExamDateService.getExamProgrammingExerciseUnlockDate(exercise);
    }
}
