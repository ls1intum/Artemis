package de.tum.cit.aet.artemis.exam.api;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.ModuleNotPresentException;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Controller
public class ExamAccessApi extends AbstractExamApi {

    private final Optional<ExamAccessService> optionalExamAccessService;

    public ExamAccessApi(Environment environment, Optional<ExamAccessService> optionalExamAccessService) {
        super(environment);
        this.optionalExamAccessService = optionalExamAccessService;
    }

    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Long examId) {
        examAccessService().checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
    }

    public void checkCourseAndExamAccessForStudentElseThrow(Long courseId, Long examId) {
        examAccessService().checkCourseAndExamAccessForStudentElseThrow(courseId, examId);
    }

    public void checkExamExerciseForExampleSolutionAccessElseThrow(Exercise examExercise) {
        examAccessService().checkExamExerciseForExampleSolutionAccessElseThrow(examExercise);
    }

    public ExamAccessService examAccessService() {
        if (optionalExamAccessService.isEmpty()) {
            throw new ModuleNotPresentException("ExamAccessService is not available");
        }

        return optionalExamAccessService.get();
    }
}
