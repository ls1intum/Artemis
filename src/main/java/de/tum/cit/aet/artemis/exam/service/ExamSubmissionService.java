package de.tum.cit.aet.artemis.exam.service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.compare.DnDMapping;
import de.tum.cit.aet.artemis.quiz.domain.compare.SAMapping;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ExamSubmissionService.class);

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
     * @return true if it is not an exam of if it is an exam and the submission is in time and the exercise is part of the user's student exam
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

        Optional<StudentExam> optionalStudentExam;
        // Since multiple student exams for a test exam might exist, find the latest unsubmitted student exam based on the created date
        if (exam.isTestExam()) {
            optionalStudentExam = studentExamRepository.findUnsubmittedStudentExamsForTestExamsWithExercisesByExamIdAndUserId(exam.getId(), user.getId()).stream()
                    .max(Comparator.comparing(StudentExam::getCreatedDate));
        }
        else {
            // for real exams, there's only one student exam per exam
            optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), exam.getId(), false);
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
        if (authorizationCheckService.isAtLeastInstructorInCourse(exam.getCourse(), user)) {
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
        // Return immediately if it is not an exam submission or if it is a programming exercise or if it is a test exam exercise
        if (!exercise.isExamExercise() || exercise instanceof ProgrammingExercise || exercise.getExam().isTestExam()) {
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

    /**
     * Returns {@code true} if the drag and drop answer submitted answer of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a drag and drop submitted answer
     * @param answer2 a drag and drop submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(DragAndDropSubmittedAnswer answer1, DragAndDropSubmittedAnswer answer2) {
        // we use a record with dragItemId and dropLocationId and use streams to create those records for both submitted answers and compare them using sets
        Set<DnDMapping> mappings1 = answer1.toDnDMapping();
        Set<DnDMapping> mappings2 = answer2.toDnDMapping();
        return Objects.equals(mappings1, mappings2);
    }

    /**
     * Returns {@code true} if the multiple choice answer submitted answer of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a multiple choice submitted answer
     * @param answer2 a multiple choice submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(MultipleChoiceSubmittedAnswer answer1, MultipleChoiceSubmittedAnswer answer2) {
        // we compare if all selected options are the same by comparing the selection option id sets, e.g. (1,3,5) vs. (2,4,5)
        Set<Long> selections1 = answer1.toSelectedIds();
        Set<Long> selections2 = answer2.toSelectedIds();
        return Objects.equals(selections1, selections2);
    }

    /**
     * Returns {@code true} if the short answer submitted answer of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a short answer submitted answer
     * @param answer2 a short answer submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(ShortAnswerSubmittedAnswer answer1, ShortAnswerSubmittedAnswer answer2) {
        // we use a record with spotId and spotText and use streams to create those records for both submitted answers and compare them using sets
        Set<SAMapping> mappings1 = answer1.toSAMappings();
        Set<SAMapping> mappings2 = answer2.toSAMappings();
        return Objects.equals(mappings1, mappings2);
    }

    /**
     * Returns {@code true} if the quiz submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param submission1 a quiz submission
     * @param submission2 a quiz submission to be compared with {@code submission1} for equality
     * @return {@code true} if the quiz submissions are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(@Nullable QuizSubmission submission1, @Nullable QuizSubmission submission2) {
        if (submission1 == null && submission2 == null) {
            return true;
        }
        else if (submission1 == null || submission2 == null) {
            return false;
        }

        var answers1 = submission1.getSubmittedAnswers();
        var answers2 = submission2.getSubmittedAnswers();
        if (answers1.size() != answers2.size()) {
            return false;
        }

        for (var answer1 : answers1) {
            for (var answer2 : answers2) {
                QuizQuestion quizQuestion1 = answer1.getQuizQuestion();
                QuizQuestion quizQuestion2 = answer2.getQuizQuestion();

                // we should still be able to compare even if the quizQuestion or the quizQuestion id is null
                if (quizQuestion1 == null || quizQuestion1.getId() == null || quizQuestion2 == null || quizQuestion2.getId() == null
                        || quizQuestion1.getId().equals(quizQuestion2.getId())) {
                    if (!isContentEqualTo(answer1, answer2)) {
                        return false;
                    }
                }
            }
        }
        // we did not find any differences
        return true;
    }

    /**
     * Returns {@code true} if the quiz submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a quiz submission
     * @param answer2 a quiz submission to be compared with {@code submission1} for equality
     * @return {@code true} if the quiz submissions are equal to each other and {@code false} otherwise; unsupported or
     *         mismatched answer types are logged and also yield {@code false}
     */
    public static boolean isContentEqualTo(SubmittedAnswer answer1, SubmittedAnswer answer2) {
        return switch (answer1) {
            case DragAndDropSubmittedAnswer dndSubmittedAnswer1 when answer2 instanceof DragAndDropSubmittedAnswer dndSubmittedAnswer2 ->
                isContentEqualTo(dndSubmittedAnswer1, dndSubmittedAnswer2);
            case MultipleChoiceSubmittedAnswer mcSubmittedAnswer1 when answer2 instanceof MultipleChoiceSubmittedAnswer mcSubmittedAnswer2 ->
                isContentEqualTo(mcSubmittedAnswer1, mcSubmittedAnswer2);
            case ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer1 when answer2 instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer2 ->
                isContentEqualTo(shortAnswerSubmittedAnswer1, shortAnswerSubmittedAnswer2);
            default -> {
                log.error("Cannot compare {} and {} for equality, classes unknown", answer1, answer2);
                yield false;
            }
        };
    }

    /**
     * Returns {@code true} if the text submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param submission1 a text submission
     * @param submission2 a text submission to be compared with {@code submission1} for equality
     * @return {@code true} if the text submissions are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(@Nullable TextSubmission submission1, @Nullable TextSubmission submission2) {
        if (submission1 == null && submission2 == null) {
            return true;
        }
        else if (submission1 == null || submission2 == null) {
            return false;
        }
        return Objects.equals(submission1.getText(), submission2.getText());
    }

    /**
     * Returns {@code true} if the modeling submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param submission1 a modeling submission
     * @param submission2 a modeling submission to be compared with {@code submission1} for equality
     * @return {@code true} if the modeling submissions are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(@Nullable ModelingSubmission submission1, @Nullable ModelingSubmission submission2) {
        if (submission1 == null && submission2 == null) {
            return true;
        }
        else if (submission1 == null || submission2 == null) {
            return false;
        }
        return Objects.equals(submission1.getModel(), submission2.getModel()) && Objects.equals(submission1.getExplanationText(), submission2.getExplanationText());
    }
}
