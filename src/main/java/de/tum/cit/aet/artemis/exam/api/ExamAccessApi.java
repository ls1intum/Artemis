package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Profile(PROFILE_CORE)
@Controller
public class ExamAccessApi extends AbstractExamApi {

    private final ExamAccessService examAccessService;

    public ExamAccessApi(ExamAccessService examAccessService) {
        this.examAccessService = examAccessService;
    }

    public void checkCourseAndExamAccessForStudentElseThrow(Long courseId, Long examId) {
        examAccessService.checkCourseAndExamAccessForStudentElseThrow(courseId, examId);
    }

    public void checkCourseAccessForInstructorElseThrow(Long courseId) {
        examAccessService.checkCourseAccessForInstructorElseThrow(courseId);
    }

    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Long examId) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
    }

    public void checkExamExerciseForExampleSolutionAccessElseThrow(Exercise examExercise) {
        examAccessService.checkExamExerciseForExampleSolutionAccessElseThrow(examExercise);
    }

    public void checkIfAllowedToGetExamResult(Exercise exercise, StudentParticipation studentParticipation, User user) {
        examAccessService.checkIfAllowedToGetExamResult(exercise, studentParticipation, user);
    }
}
