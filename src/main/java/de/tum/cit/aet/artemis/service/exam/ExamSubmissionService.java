package de.tum.cit.aet.artemis.service.exam;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.domain.exam.StudentExam;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ParticipationService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
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
     * @param exercise the exercise for which a submission should be saved
     * @param user     the user that wants to submit
     */
    public void checkSubmissionAllowanceElseThrow(Exercise exercise, User user) {
        if (!isAllowedToSubmitDuringExam(exercise, user, false)) {
            throw new AccessForbiddenException("Submission not allowed for " + user.getLogin() + " for exercise " + exercise.getId() + " in the exam.");
        }
    }

    /**
     * Check if the user is allowed to submit (submission is in time & user's student exam has the exercise or it is a test run).
     * Note: if the exercise is not an exam, this method will return true
     *
     * @param exercise        the exercise for which a submission should be saved
     * @param user            the user that wants to submit
     * @param withGracePeriod whether the grace period should be taken into account or not
     * @return true if it is not an exam of if it is an exam and the submission is in time and the exercise is part of
     *         the user's student exam
     *         TODO: Simplify this method and potentially usages of it by using {@link ProgrammingExerciseStudentParticipation#isLocked()}.
     */
    public boolean isAllowedToSubmitDuringExam(Exercise exercise, User user, boolean withGracePeriod) {
        if (!exercise.isExamExercise()) {
            return true;
        }

        Exam exam = exercise.getExerciseGroup().getExam();
        Optional<StudentExam> optionalStudentExam = findStudentExamForUser(user, exam);
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

        // Users are only allowed to access exercises that are part of their own student exam
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

    private Optional<StudentExam> findStudentExamForUser(User user, Exam exam) {
        // Step 1: Find real exam
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), exam.getId(), false);
        if (optionalStudentExam.isEmpty()) {
            // Step 2: Find latest (=the highest id) unsubmitted test exam
            optionalStudentExam = studentExamRepository.findUnsubmittedStudentExamsForTestExamsWithExercisesByExamIdAndUserId(exam.getId(), user.getId()).stream()
                    .max(Comparator.comparing(StudentExam::getId));
        }
        return optionalStudentExam;
    }

    /**
     * Check if the submission is made as part of a test run exam
     * Only Instructors have access to test runs.
     *
     * @param exercise the exercise
     * @param user     the user
     * @param exam     the exam
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
     * deviates from the one we've got from the database.
     * - If no submission exists (on creation) we allow adding one (implicitly via repository.save()).
     * <p>
     * TODO: we might want to move this to the SubmissionService
     *
     * @param exercise   the exercise for which the submission should be saved
     * @param submission the submission
     * @param user       the current user
     * @return the submission. If a submission already exists for the exercise we will set the id
     */
    public Submission preventMultipleSubmissions(Exercise exercise, Submission submission, User user) {
        // Return immediately if it is not an exam submissions or if it is a programming exercise
        if (!exercise.isExamExercise() || exercise instanceof ProgrammingExercise) {
            return submission;
        }

        List<StudentParticipation> participations = participationService.findByExerciseAndStudentIdWithEagerSubmissions(exercise, user.getId());
        if (!participations.isEmpty()) {
            Set<Submission> submissions = participations.getFirst().getSubmissions();
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
