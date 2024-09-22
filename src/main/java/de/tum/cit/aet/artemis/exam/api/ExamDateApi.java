package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Controller
public class ExamDateApi extends AbstractExamApi {

    private final Optional<StudentExamRepository> optionalStudentExamRepository;

    private final Optional<ExamDateService> optionalExamDateService;

    public ExamDateApi(Environment environment, Optional<StudentExamRepository> optionalStudentExamRepository, Optional<ExamDateService> optionalExamDateService) {
        super(environment);
        this.optionalStudentExamRepository = optionalStudentExamRepository;
        this.optionalExamDateService = optionalExamDateService;
    }

    public ZonedDateTime getLatestIndividualExamEndDateWithoutGracePeriod(Long examId) {
        return getOrThrow(optionalExamDateService).getLatestIndividualExamEndDate(examId);
    }

    public boolean isExamWithGracePeriodOver(Exam exam) {
        return getOrThrow(optionalExamDateService).isExamWithGracePeriodOver(exam);
    }

    public boolean isIndividualExerciseWorkingPeriodOver(Exam exam, StudentParticipation studentParticipation) {
        return getOrThrow(optionalExamDateService).isIndividualExerciseWorkingPeriodOver(exam, studentParticipation);
    }

    public ZonedDateTime getIndividualDueDate(Exercise exercise, StudentParticipation participation) {
        StudentExam studentExam = getOrThrow(optionalStudentExamRepository).findStudentExam(exercise, participation).orElse(null);
        if (studentExam == null) {
            return exercise.getDueDate();
        }
        return studentExam.getExam().getStartDate().plusSeconds(studentExam.getWorkingTime());
    }
}
