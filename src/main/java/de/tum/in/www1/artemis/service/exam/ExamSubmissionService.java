package de.tum.in.www1.artemis.service.exam;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ExamSubmissionService {

    private final StudentExamRepository studentExamRepository;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExamRepository examRepository;

    public ExamSubmissionService(StudentExamRepository studentExamRepository, ExamRepository examRepository, ParticipationService participationService,
            AuthorizationCheckService authorizationCheckService) {
        this.studentExamRepository = studentExamRepository;
        this.examRepository = examRepository;
        this.participationService = participationService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Check if the submission is an exam submission and if so, check that the current user is allowed to submit.
     *
     * @param exercise  the exercise for which a submission should be saved
     * @param user      the user that wants to submit
     */
    public void checkSubmissionAllowanceElseThrow(Exercise exercise, User user) {
        if (!isAllowedToSubmitDuringExam(exercise, user, false)) {
            throw new AccessForbiddenException();
        }
    }

    /**
     * Check if the user is allowed to submit (submission is in time & user's student exam has the exercise or it is a test run).
     * Note: if the exercise is not an exam, this method will return true
     *
     * @param exercise  the exercise for which a submission should be saved
     * @param user      the user that wants to submit
     * @param withGracePeriod whether the grace period should be taken into account or not
     * @return true if it is not an exam of if it is an exam and the submission is in time and the exercise is part of
     *         the user's student exam
     */
    public boolean isAllowedToSubmitDuringExam(Exercise exercise, User user, boolean withGracePeriod) {
        if (isExamSubmission(exercise)) {
            // Get the student exam if it was not passed to the function
            Exam exam = exercise.getExerciseGroup().getExam();
            Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), exam.getId());
            if (optionalStudentExam.isEmpty()) {
                // We check for test exams here for performance issues as this will not be the case for all students who are participating in the exam
                // isAllowedToSubmitDuringExam is called everytime an exercise is saved (e.g. auto save every 30 seconds for every student) therefore it is best to limit
                // unnecessary database calls
                if (!isExamTestRunSubmission(exercise, user, exam)) {
                    throw new EntityNotFoundException("Student exam with for userId \"" + user.getId() + "\" and examId \"" + exam.getId() + "\" does not exist");
                }
                return true;
            }
            StudentExam studentExam = optionalStudentExam.get();
            // Check that the current user is allowed to submit to this exercise
            if (!studentExam.getExercises().contains(exercise)) {
                return false;
            }

            // if the student exam was already submitted, the user cannot save anymore
            if (Boolean.TRUE.equals(studentExam.isSubmitted()) || studentExam.getSubmissionDate() != null) {
                return false;
            }

            // Check that the submission is in time
            return isSubmissionInTime(exercise, studentExam, withGracePeriod);
        }
        return true;
    }

    /**
     * Check if the submission is made as part of a test run exam
     * Only Instructors have access to test runs.
     * @param exercise the exercise
     * @param user the user
     * @param exam the exam
     * @return returns whether the submission is part of a test run exam.
     */
    private boolean isExamTestRunSubmission(Exercise exercise, User user, Exam exam) {
        // Check if user is an instructor or admin
        if (user.getGroups().contains(exam.getCourse().getInstructorGroupName()) || authorizationCheckService.isAdmin(user)) {
            // fetch all testRuns for the instructor
            List<StudentExam> testRuns = studentExamRepository.findAllTestRunsWithExercisesByExamIdForUser(exam.getId(), user.getId());
            // if a test run contains the exercise, then the instructor is allowed to submit
            return testRuns.stream().anyMatch(testRun -> testRun.getExercises().contains(exercise));
        }
        // only instructors can access and submit to test runs
        return false;
    }

    /**
     * We want to prevent multiple submissions for text, modeling, file upload and quiz exercises. Therefore we check if
     * a submission for this exercise+student already exists.
     * - If a submission exists, we will always overwrite this submission, even if the id of the received submission
     *   deviates from the one we've got from the database.
     * - If no submission exists (on creation) we allow adding one (implicitly via repository.save()).
     *
     * TODO: we might want to move this to the SubmissionService
     *
     * @param exercise      the exercise for which the submission should be saved
     * @param submission    the submission
     * @param user          the current user
     * @return the submission. If a submission already exists for the exercise we will set the id
     */
    public Submission preventMultipleSubmissions(Exercise exercise, Submission submission, User user) {
        // Return immediately if it is not an exam submissions or if it is a programming exercise
        if (!isExamSubmission(exercise) || exercise instanceof ProgrammingExercise) {
            return submission;
        }

        List<StudentParticipation> participations = participationService.findByExerciseAndStudentIdWithEagerSubmissions(exercise, user.getId());
        if (!participations.isEmpty()) {
            Set<Submission> submissions = participations.get(0).getSubmissions();
            if (!submissions.isEmpty()) {
                Submission existingSubmission = submissions.iterator().next();
                // Instead of creating a new submission, we want to overwrite the already existing submission. Therefore
                // we set the id of the received submission to the id of the existing submission. When repository.save()
                // is invoked the existing submission will be updated.
                submission.setId(existingSubmission.getId());
            }
        }

        return submission;
    }

    private boolean isExamSubmission(Exercise exercise) {
        return exercise.isExamExercise();
    }

    private boolean isSubmissionInTime(Exercise exercise, StudentExam studentExam, boolean withGracePeriod) {
        // The attributes of the exam (e.g. startDate) are missing. Therefore we need to load it.
        Exam exam = examRepository.findByIdElseThrow(exercise.getExerciseGroup().getExam().getId());
        ZonedDateTime calculatedEndDate = withGracePeriod ? exam.getEndDate().plusSeconds(exam.getGracePeriod()) : exam.getEndDate();
        if (studentExam.getWorkingTime() != null && studentExam.getWorkingTime() > 0) {
            calculatedEndDate = withGracePeriod ? studentExam.getIndividualEndDateWithGracePeriod() : studentExam.getIndividualEndDate();
        }
        return exam.getStartDate().isBefore(ZonedDateTime.now()) && calculatedEndDate.isAfter(ZonedDateTime.now());
    }
}
