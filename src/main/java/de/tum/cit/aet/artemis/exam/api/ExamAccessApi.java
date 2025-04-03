package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
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

    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Long examId) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
    }

    public void checkExamExerciseForExampleSolutionAccessElseThrow(Exercise examExercise) {
        examAccessService.checkExamExerciseForExampleSolutionAccessElseThrow(examExercise);
    }

    /**
     * Checks if the user is allowed to see the exam result.
     * Otherwise, throws a {@link AccessForbiddenException}.
     *
     * @param examExercise         - Exam Exercise that the result is requested for
     * @param studentParticipation - used to retrieve the individual exam working time
     * @param user                 - User that requests the result
     * @throws ConflictException if examExercise does not belong to an exam
     */
    public void checkIfAllowedToGetExamResult(Exercise examExercise, StudentParticipation studentParticipation, User user) {
        examAccessService.checkIfAllowedToGetExamResult(examExercise, studentParticipation, user);
    }
}
