package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

@Service
public class ExamSubmissionService {

    private final StudentExamService studentExamService;

    private final ExamService examService;

    public ExamSubmissionService(StudentExamService studentExamService, ExamService examService) {
        this.studentExamService = studentExamService;
        this.examService = examService;
    }

    /**
     * Check if the submission is a exam submission and if so, check that the current user is allowed to submit.
     *
     * @param exercise  the exercise for which a submission should be saved
     * @param user      the user that wants to submit
     * @param <T>       The type of the return type of the requesting route so that the
     *                  response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkSubmissionAllowance(Exercise exercise, User user) {
        // Only apply the additional check if it is an exam submission
        if (isExamSubmission(exercise)) {
            // Check that the current user is allowed to submit to this exercise
            Exam exam = exercise.getExerciseGroup().getExam();
            StudentExam studentExam = studentExamService.findOneByUserIdAndExamId(user.getId(), exam.getId());
            if (studentExam.getExercises().contains(exercise)) {
                // TODO: improve the error message sent to the client
                return Optional.of(forbidden());
            }

            if (!isSubmissionInTime(exercise, studentExam)) {
                // TODO: improve the error message sent to the client
                return Optional.of(forbidden());
            }
        }
        return Optional.empty();
    }

    private boolean isExamSubmission(Exercise exercise) {
        return exercise.hasExerciseGroup();
    }

    private boolean isSubmissionInTime(Exercise exercise, StudentExam studentExam) {
        // TODO: we might want to add a grace period here. If so we have to adjust the dueDate checks in the submission
        // services (e.g. in TextSubmissionService::handleTextSubmission())
        // The attributes of the exam (e.g. startDate) are missing. Therefore we need to load it again.
        Exam exam = examService.findOne(exercise.getExerciseGroup().getExam().getId());
        ZonedDateTime calculatedEndDate = exam.getEndDate();
        if (studentExam.getWorkingTime() != null && studentExam.getWorkingTime() > 0) {
            calculatedEndDate = exam.getStartDate().plusSeconds(studentExam.getWorkingTime());
        }
        return exam.getStartDate().isBefore(ZonedDateTime.now()) && calculatedEndDate.isAfter(ZonedDateTime.now());
    }
}
