package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.Exam;

public class ExamSubmissionService {

    private final StudentExamService studentExamService;

    public ExamSubmissionService(StudentExamService studentExamService) {
        this.studentExamService = studentExamService;
    }

    /**
     * Check if the submission is a exam submission and if so, check that the current user is allowed to submit.
     *
     * @param exercise  the exercise for which a submission should be saved
     * @param <T>       The type of the return type of the requesting route so that the
     *                  response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkSubmissionAllowance(Exercise exercise) {
        // Only apply the additional check if it is an exam submission
        if (isExamSubmission(exercise)) {
            if (!isSubmissionInTime(exercise)) {
                // TODO: improve the error message sent to the client
                return Optional.of(forbidden());
            }
        }
        return Optional.empty();
    }

    private boolean isExamSubmission(Exercise exercise) {
        return exercise.hasExerciseGroup();
    }

    private boolean isSubmissionInTime(Exercise exercise) {
        // TODO: use the students working time
        // TODO: we might want to add a grace period here
        Exam exam = exercise.getExerciseGroup().getExam();
        return exam.getStartDate().isBefore(ZonedDateTime.now()) && exam.getEndDate().isAfter(ZonedDateTime.now());
    }
}
