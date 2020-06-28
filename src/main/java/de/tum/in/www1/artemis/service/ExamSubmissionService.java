package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

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
            StudentExam studentExam = studentExamService.findOneWithExercisesByUserIdAndExamId(user.getId(), exam.getId());
            if (!studentExam.getExercises().contains(exercise)) {
                // TODO: improve the error message sent to the client
                return Optional.of(forbidden());
            }

            // Check that the submission is in time
            if (!isSubmissionInTime(exercise, studentExam)) {
                // TODO: improve the error message sent to the client
                return Optional.of(forbidden());
            }
        }
        return Optional.empty();
    }

    /**
     * We want to prevent multiple submissions for text, modeling, file upload and quiz exercises. Therefore we check if
     * a submission for this exercise+student already exists.
     * - If a submission exists, we will always overwrite this submission, even if the id of the received submission
     *   deviates from the one we've got from the database.
     * - If no submission exists (on creation) we allow adding one (implicitly via repository.save()).
     *
     * NOTE: this method requires that student participations and submissions are part of the exercise
     *
     * @param exercise      the exercise for which the submission should be saved
     * @param submission    the submission
     * @return the submission. If a submission already exists for the exercise we will set the id
     */
    public Submission preventMultipleSubmissions(Exercise exercise, Submission submission) {
        // Return immediately if it is not a exam submissions or if it is a programming exercise
        if (!isExamSubmission(exercise) || exercise.getClass() == ProgrammingExercise.class) {
            return submission;
        }

        Set<StudentParticipation> participations = exercise.getStudentParticipations();
        if (!participations.isEmpty()) {
            Set<Submission> submissions = participations.iterator().next().getSubmissions();
            if (!submissions.isEmpty()) {
                Submission existingSubmission = submissions.iterator().next();
                submission.setId(existingSubmission.getId());
            }
        }

        return submission;
    }

    private boolean isExamSubmission(Exercise exercise) {
        return exercise.hasExerciseGroup();
    }

    private boolean isSubmissionInTime(Exercise exercise, StudentExam studentExam) {
        // TODO: we might want to add a grace period here. If so we have to adjust the dueDate checks in the submission
        // services (e.g. in TextSubmissionService::handleTextSubmission())
        // The attributes of the exam (e.g. startDate) are missing. Therefore we need to load it.
        Exam exam = examService.findOne(exercise.getExerciseGroup().getExam().getId());
        ZonedDateTime calculatedEndDate = exam.getEndDate();
        if (studentExam.getWorkingTime() != null && studentExam.getWorkingTime() > 0) {
            calculatedEndDate = exam.getStartDate().plusSeconds(studentExam.getWorkingTime());
        }
        return exam.getStartDate().isBefore(ZonedDateTime.now()) && calculatedEndDate.isAfter(ZonedDateTime.now());
    }
}
