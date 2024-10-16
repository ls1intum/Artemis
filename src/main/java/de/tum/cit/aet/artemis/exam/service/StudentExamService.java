package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_EXERCISE_START_STATUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.TimeLogUtil.formatDurationFrom;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionVersionService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.compare.DnDMapping;
import de.tum.cit.aet.artemis.quiz.domain.compare.SAMapping;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizPoolService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;

/**
 * Service Implementation for managing StudentExam.
 */
@Profile(PROFILE_CORE)
@Service
public class StudentExamService {

    private static final String EXAM_EXERCISE_START_STATUS_TOPIC = "/topic/exams/%s/exercise-start-status";

    private static final Logger log = LoggerFactory.getLogger(StudentExamService.class);

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final SubmissionService submissionService;

    private final ExamQuizService examQuizService;

    private final SubmissionVersionService submissionVersionService;

    private final StudentExamRepository studentExamRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExamRepository examRepository;

    private final CacheManager cacheManager;

    private final WebsocketMessagingService websocketMessagingService;

    private final TaskScheduler scheduler;

    private final ExamQuizQuestionsGenerator examQuizQuestionsGenerator;

    public StudentExamService(StudentExamRepository studentExamRepository, UserRepository userRepository, ParticipationService participationService,
            QuizSubmissionRepository quizSubmissionRepository, SubmittedAnswerRepository submittedAnswerRepository, TextSubmissionRepository textSubmissionRepository,
            ModelingSubmissionRepository modelingSubmissionRepository, SubmissionVersionService submissionVersionService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, SubmissionService submissionService,
            StudentParticipationRepository studentParticipationRepository, ExamQuizService examQuizService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingTriggerService programmingTriggerService, ExamRepository examRepository, CacheManager cacheManager, WebsocketMessagingService websocketMessagingService,
            @Qualifier("taskScheduler") TaskScheduler scheduler, QuizPoolService quizPoolService) {
        this.participationService = participationService;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.submissionVersionService = submissionVersionService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.examQuizService = examQuizService;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.examRepository = examRepository;
        this.cacheManager = cacheManager;
        this.websocketMessagingService = websocketMessagingService;
        this.scheduler = scheduler;
        this.examQuizQuestionsGenerator = quizPoolService;
    }

    /**
     * Submit StudentExam and uses submissions as final submissions if studentExam is not yet submitted
     * and if it was submitted after exam startDate and before individual endDate + gracePeriod
     *
     * @param existingStudentExam   the existing student exam object in the database
     * @param studentExamFromClient the student exam object from the client which will be submitted (final submission)
     * @param currentUser           the current user
     */
    public void submitStudentExam(StudentExam existingStudentExam, StudentExam studentExamFromClient, User currentUser) {
        log.debug("Submit student exam with id {}", studentExamFromClient.getId());

        long start = System.nanoTime();
        // most important aspect here: set studentExam to submitted and set submission date
        // 3. DB Call: write
        submitStudentExam(studentExamFromClient);
        log.debug("    Set student exam to submitted in {}", formatDurationFrom(start));

        start = System.nanoTime();
        try {
            // in case there were last second changes, that have not been submitted yet.
            saveSubmissions(studentExamFromClient, currentUser);
        }
        catch (Exception e) {
            log.error("saveSubmissions threw an exception", e);
        }
        log.debug("    Potentially save submissions in {}", formatDurationFrom(start));

        start = System.nanoTime();
        // NOTE: only for real exams and test exams, the student repositories need to be locked
        // For test runs, this is not needed, because instructors have admin permissions on the VCS project (which contains the repository) anyway
        if (!studentExamFromClient.isTestRun()) {
            try {
                // lock the programming exercise repository access (important in case of early exam submissions), only when the student hands in early (asynchronously)
                programmingExerciseParticipationService.lockStudentRepositories(currentUser, existingStudentExam);
            }
            catch (Exception e) {
                log.error("lockStudentRepositories threw an exception", e);
            }
        }

        log.debug("    Lock student repositories in {}", formatDurationFrom(start));
        // NOTE: from here on, we only handle test runs and test exams

        if (!studentExamFromClient.isTestRun() && !studentExamFromClient.isTestExam()) {
            return;
        }

        // NOTE: only for test runs and test exams, the quizzes should be evaluated automatically
        // immediately evaluate quiz participations for test runs and test exams
        examQuizService.evaluateQuizParticipationsForTestRunAndTestExam(studentExamFromClient);

        // Trigger build for all programing participations
        var currentStudentParticipations = studentExamFromClient.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise)
                .flatMap(exercise -> studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerLegalSubmissions(exercise.getId(), currentUser.getId()).stream())
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();

        if (!currentStudentParticipations.isEmpty()) {
            // Delay to ensure that "Building and testing" is shown in the client
            scheduler.schedule(() -> programmingTriggerService.triggerBuildForParticipations(currentStudentParticipations), Instant.now().plus(3, ChronoUnit.SECONDS));
        }
    }

    private void submitStudentExam(StudentExam studentExam) {
        var now = ZonedDateTime.now();
        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(now);
        studentExamRepository.submitStudentExam(studentExam.getId(), now);
    }

    private void saveSubmissions(StudentExam studentExam, User currentUser) {
        // we only need to save submissions for modeling, text and quiz exercises;
        var relevantExercises = studentExam.getExercises().stream().filter(ex -> !(ex instanceof ProgrammingExercise) && !(ex instanceof FileUploadExercise)).toList();
        if (relevantExercises.isEmpty()) {
            // nothing to save
            return;
        }
        // 4. DB Call: read
        List<StudentParticipation> existingRelevantParticipations = studentParticipationRepository.findByStudentExamWithEagerSubmissions(studentExam, relevantExercises);

        for (Exercise exercise : studentExam.getExercises()) {
            // we do not apply the following checks for programming exercises or file upload exercises
            try {
                saveSubmission(currentUser, existingRelevantParticipations, exercise);
            }
            catch (Exception e) {
                log.error("saveSubmission threw an exception", e);
            }
        }
    }

    private void saveSubmission(User currentUser, List<StudentParticipation> existingRelevantParticipations, Exercise exercise) {
        if (exercise instanceof ProgrammingExercise) {
            // programming submissions are only saved during submit in their respective submission page or git push
            return;
        }
        if (exercise instanceof FileUploadExercise) {
            // file upload submissions are only saved during submit in their respective submission page
            return;
        }

        // if exercise is either QuizExercise, TextExercise or ModelingExercise and exactly one participation exists
        if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
            // this object comes from the client
            StudentParticipation studentParticipationFromClient = exercise.getStudentParticipations().iterator().next();
            // this object comes from the database
            StudentParticipation existingParticipationInDatabase = existingRelevantParticipations.stream().filter(p -> p.getId().equals(studentParticipationFromClient.getId()))
                    .findFirst().orElseThrow();
            // if exactly one submission exists we save the submission
            if (studentParticipationFromClient.getSubmissions() != null && studentParticipationFromClient.getSubmissions().size() == 1) {
                // check that the current user owns the participation
                if (!studentParticipationFromClient.isOwnedBy(currentUser) || !existingParticipationInDatabase.isOwnedBy(currentUser)) {
                    throw new AccessForbiddenException("User " + currentUser.getLogin() + " is not allowed to access the participation " + existingParticipationInDatabase.getId());
                }
                studentParticipationFromClient.setExercise(exercise);

                Submission submissionFromClient = studentParticipationFromClient.getSubmissions().iterator().next();

                // check that the submission belongs to the already saved participation
                if (!existingParticipationInDatabase.getSubmissions().contains(submissionFromClient)) {
                    throw new AccessForbiddenException("User " + currentUser.getLogin() + " cannot submit a different submission " + submissionFromClient + " for participation "
                            + existingParticipationInDatabase.getId());
                }
                // check that no result has been injected
                if (submissionFromClient.getLatestResult() != null) {
                    throw new AccessForbiddenException("User " + currentUser.getLogin() + " cannot inject a result " + submissionFromClient.getLatestResult() + " for submission "
                            + submissionFromClient + " and participation " + existingParticipationInDatabase.getId());
                }
                submissionFromClient.setParticipation(studentParticipationFromClient);
                submissionFromClient.submissionDate(ZonedDateTime.now());
                submissionFromClient.submitted(true);
                if (exercise instanceof QuizExercise) {
                    // recreate pointers back to submission in each submitted answer
                    for (SubmittedAnswer submittedAnswer : ((QuizSubmission) submissionFromClient).getSubmittedAnswers()) {
                        submittedAnswer.setSubmission(((QuizSubmission) submissionFromClient));
                        if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                            ((DragAndDropSubmittedAnswer) submittedAnswer).getMappings()
                                    .forEach(dragAndDropMapping -> dragAndDropMapping.setSubmittedAnswer(((DragAndDropSubmittedAnswer) submittedAnswer)));
                        }
                        else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                            ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts()
                                    .forEach(submittedText -> submittedText.setSubmittedAnswer(((ShortAnswerSubmittedAnswer) submittedAnswer)));
                        }
                    }

                    // load quiz submissions for existing participation to be able to compare them in saveSubmission
                    // 5. DB Call: read
                    submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(List.of(existingParticipationInDatabase));

                    QuizSubmission existingSubmissionInDatabase = (QuizSubmission) existingParticipationInDatabase.findLatestSubmission().orElse(null);
                    QuizSubmission quizSubmissionFromClient = (QuizSubmission) submissionFromClient;

                    if (!isContentEqualTo(existingSubmissionInDatabase, quizSubmissionFromClient)) {
                        quizSubmissionRepository.save(quizSubmissionFromClient);
                        saveSubmissionVersion(currentUser, submissionFromClient);
                    }
                }
                else if (exercise instanceof TextExercise) {
                    TextSubmission existingSubmissionInDatabase = (TextSubmission) existingParticipationInDatabase.findLatestSubmission().orElse(null);
                    TextSubmission textSubmissionFromClient = (TextSubmission) submissionFromClient;
                    if (!isContentEqualTo(existingSubmissionInDatabase, textSubmissionFromClient)) {
                        textSubmissionRepository.save(textSubmissionFromClient);
                        saveSubmissionVersion(currentUser, submissionFromClient);
                    }
                }
                else if (exercise instanceof ModelingExercise) {
                    ModelingSubmission existingSubmissionInDatabase = (ModelingSubmission) existingParticipationInDatabase.findLatestSubmission().orElse(null);
                    ModelingSubmission modelingSubmissionFromClient = (ModelingSubmission) submissionFromClient;
                    if (!isContentEqualTo(existingSubmissionInDatabase, modelingSubmissionFromClient)) {
                        modelingSubmissionRepository.save(modelingSubmissionFromClient);
                        saveSubmissionVersion(currentUser, submissionFromClient);
                    }
                }
            }
        }
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
     * @return {@code true} if the quiz submissions are equal to each other and {@code false} otherwise
     * @throws RuntimeException if the answer types are not supported
     */
    public static boolean isContentEqualTo(SubmittedAnswer answer1, SubmittedAnswer answer2) {
        if (answer1 instanceof DragAndDropSubmittedAnswer submittedAnswer1 && answer2 instanceof DragAndDropSubmittedAnswer submittedAnswer2) {
            return isContentEqualTo(submittedAnswer1, submittedAnswer2);
        }
        else if (answer1 instanceof MultipleChoiceSubmittedAnswer submittedAnswer1 && answer2 instanceof MultipleChoiceSubmittedAnswer submittedAnswer2) {
            return isContentEqualTo(submittedAnswer1, submittedAnswer2);
        }
        else if (answer1 instanceof ShortAnswerSubmittedAnswer submittedAnswer1 && answer2 instanceof ShortAnswerSubmittedAnswer submittedAnswer2) {
            return isContentEqualTo(submittedAnswer1, submittedAnswer2);
        }
        log.error("Cannot compare {} and {} for equality, classes unknown", answer1, answer2);
        return false;
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

    private void saveSubmissionVersion(User currentUser, Submission submissionFromClient) {
        // versioning of submission
        try {
            submissionVersionService.saveVersionForIndividual(submissionFromClient, currentUser);
        }
        catch (Exception ex) {
            log.error("Submission version could not be saved", ex);
        }
    }

    /**
     * Assess all exercises, except quiz exercises, of student exams of an exam which are not submitted with 0 points.
     *
     * @param exam     the exam
     * @param assessor the assessor should be the instructor making the call.
     * @return returns the set of unsubmitted StudentExams, the participations of which were assessed
     */
    public Set<StudentExam> assessUnsubmittedStudentExams(final Exam exam, final User assessor) {
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam.getId());
        Map<User, List<Exercise>> exercisesOfUser = getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            // fetch all studentParticipations of a user, with submissions and results eagerly loaded
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));

            for (final var studentParticipation : studentParticipations) {
                var latestSubmission = studentParticipation.findLatestSubmission();
                latestSubmission = prepareProgrammingSubmission(latestSubmission, studentParticipation);
                if (latestSubmission.isPresent()) {
                    for (int correctionRound = 0; correctionRound < exam.getNumberOfCorrectionRoundsInExam(); correctionRound++) {
                        // required so that the submission is counted in the assessment dashboard
                        latestSubmission.get().submitted(true);
                        submissionService.addResultWithFeedbackByCorrectionRound(studentParticipation, assessor, 0D, "You did not submit your exam", correctionRound);
                    }
                }
            }
        }
        return unsubmittedStudentExams;
    }

    /**
     * Assess the modeling-, file upload and text submissions of an exam which are empty.
     * Also create automatic submissions and assessments for programming exercises without submissions.
     * Also sets the state of all participations for all student exams which were submitted to FINISHED
     *
     * @param exam                the exam
     * @param assessor            the assessor should be the instructor making the call
     * @param excludeStudentExams studentExams which should be excluded. This is used to exclude unsubmitted student exams because they are already assessed, see
     *                                {@link StudentExamService#assessUnsubmittedStudentExams}
     * @return returns the set of StudentExams of which the empty submissions were assessed
     */
    public Set<StudentExam> assessEmptySubmissionsOfStudentExams(final Exam exam, final User assessor, final Set<StudentExam> excludeStudentExams) {
        Set<StudentExam> studentExams = studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(exam.getId());
        // remove student exams which should be excluded
        studentExams = studentExams.stream().filter(studentExam -> !excludeStudentExams.contains(studentExam)).collect(Collectors.toSet());
        Map<User, List<Exercise>> exercisesOfUser = getExercisesOfUserMap(studentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (var studentParticipation : studentParticipations) {
                // even if the student did not submit anything for a specific exercise (the InitializationState is therefore only INITIALIZED)
                // we want to set it to FINISHED as the exam was handed in.
                if (studentParticipation.getInitializationState().equals(InitializationState.INITIALIZED)) {
                    studentParticipation.setInitializationState(InitializationState.FINISHED);
                    studentParticipationRepository.save(studentParticipation);
                }
                var latestSubmission = studentParticipation.findLatestSubmission();
                boolean wasEmptyProgrammingParticipation = false;
                if (latestSubmission.isEmpty() && studentParticipation.getExercise() instanceof ProgrammingExercise) {
                    wasEmptyProgrammingParticipation = true;
                    latestSubmission = prepareProgrammingSubmission(latestSubmission, studentParticipation);
                }
                if (latestSubmission.isPresent() && (latestSubmission.get().isEmpty() || wasEmptyProgrammingParticipation)) {
                    for (int correctionRound = 0; correctionRound < exam.getNumberOfCorrectionRoundsInExam(); correctionRound++) {
                        // required so that the submission is counted in the assessment dashboard
                        latestSubmission.get().submitted(true);
                        submissionService.addResultWithFeedbackByCorrectionRound(studentParticipation, assessor, 0D, "Empty submission", correctionRound);
                    }
                }
            }
        }
        return studentExams;
    }

    /**
     * Helper method to return a map for each user to their exercises. Filters out quiz exercises as they are assessed differently.
     *
     * @param studentExams the student exams of the users containing the exercises
     * @return a map of the User as key, and a list of the users exercises as value
     */
    public Map<User, List<Exercise>> getExercisesOfUserMap(Set<StudentExam> studentExams) {
        return studentExams.stream().collect(
                Collectors.toMap(StudentExam::getUser, studentExam -> studentExam.getExercises().stream().filter(exercise -> !(exercise instanceof QuizExercise)).toList()));
    }

    /**
     * Prepares the submission for programming participations.
     * When it is the participation of a programming exercise and the manual assessment is enabled, but there is no submission,
     * a new submission for the programming participation needs to be created.
     *
     * @param latestSubmission     the optional latest submission of the participation
     * @param studentParticipation the provided ProgrammingStudentParticipation
     * @return the latestSubmission
     */
    public Optional<Submission> prepareProgrammingSubmission(Optional<Submission> latestSubmission, StudentParticipation studentParticipation) {
        if (latestSubmission.isEmpty() && studentParticipation.getExercise() instanceof ProgrammingExercise programmingExercise && programmingExercise.areManualResultsAllowed()) {
            submissionService.addEmptyProgrammingSubmissionToParticipation(studentParticipation);
            return studentParticipation.findLatestSubmission();
        }
        return latestSubmission;
    }

    /**
     * Generates a Student Exam marked as a testRun for the instructor to test the exam as a student would experience it.
     * Calls {@link StudentExamService#generateTestRun and {@link ExamService#setUpTestRunExerciseParticipationsAndSubmissions}}
     *
     * @param testRunConfiguration the configured studentExam
     * @return the created testRun studentExam
     */
    public StudentExam createTestRun(StudentExam testRunConfiguration) {
        StudentExam testRun = generateTestRun(testRunConfiguration);
        setUpTestRunExerciseParticipationsAndSubmissions(testRun.getId());
        return testRun;
    }

    /**
     * Create TestRun student exam based on the configuration provided.
     *
     * @param testRunConfiguration Contains the exercises and working time for this test run
     * @return The created test run
     */
    private StudentExam generateTestRun(StudentExam testRunConfiguration) {
        StudentExam testRun = new StudentExam();
        testRun.setExercises(testRunConfiguration.getExercises());
        testRun.setExam(testRunConfiguration.getExam());
        testRun.setWorkingTime(testRunConfiguration.getWorkingTime());
        testRun.setUser(userRepository.getUser());
        testRun.setTestRun(true);
        testRun.setSubmitted(false);
        testRun = studentExamRepository.save(testRun);
        return testRun;
    }

    /**
     * Sets up the participations and submissions for all the exercises of the test run.
     * Calls {@link StudentExamService#setUpExerciseParticipationsAndSubmissions} to set up the exercise participations.
     */
    private void setUpTestRunExerciseParticipationsAndSubmissions(Long testRunId) {
        StudentExam testRun = studentExamRepository.findWithExercisesParticipationsSubmissionsById(testRunId, true)
                .orElseThrow(() -> new EntityNotFoundException("StudentExam with id:" + testRunId + "does not exist"));
        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());
        setUpExerciseParticipationsAndSubmissions(testRun, generatedParticipations);
        // use the flag test run for all participations of the created test run
        generatedParticipations.forEach(studentParticipation -> studentParticipation.setTestRun(true));
        studentParticipationRepository.saveAll(generatedParticipations);
    }

    /**
     * Method to set up new participations for a StudentExam of a test exam.
     *
     * @param studentExam the studentExam for which the new participations should be set up
     */
    public void setUpTestExamExerciseParticipationsAndSubmissions(StudentExam studentExam) {
        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());
        setUpExerciseParticipationsAndSubmissions(studentExam, generatedParticipations);
        // TODO: Michael Allgaier: schedule a lock operation for all involved student repositories of this student exam (test exam) at the end of the individual working time
        studentParticipationRepository.saveAll(generatedParticipations);
    }

    /**
     * Sets up the participations and submissions for all the exercises of the student exam.
     *
     * @param studentExam             The studentExam for which the participations and submissions should be created
     * @param generatedParticipations List of generated participations to track how many participations have been generated
     */
    public void setUpExerciseParticipationsAndSubmissions(StudentExam studentExam, List<StudentParticipation> generatedParticipations) {
        User student = studentExam.getUser();

        for (Exercise exercise : studentExam.getExercises()) {
            // NOTE: the following code is performed in parallel threads, therefore we need to set the authorization here
            SecurityUtils.setAuthorizationObject();
            // NOTE: it's not ideal to invoke the next line several times (2000 student exams with 10 exercises would lead to 20.000 database calls to find all participations).
            // One optimization could be that we load all participations per exercise once (or per exercise) into a large list (10 * 2000 = 20.000 participations) and then check if
            // those participations exist in Java, however this might lead to memory issues and might be more difficult to program (and more difficult to understand)
            // TODO: directly check in the database if the entry exists for the student, exercise and InitializationState.INITIALIZED
            var studentParticipations = participationService.findByExerciseAndStudentId(exercise, student.getId());
            // we start the exercise if no participation was found that was already fully initialized
            if (studentExam.isTestExam() || studentParticipations.stream().noneMatch(studentParticipation -> studentParticipation.getParticipant().equals(student)
                    && studentParticipation.getInitializationState() != null && studentParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                try {
                    // Load lazy property
                    if (exercise instanceof ProgrammingExercise programmingExercise && !Hibernate.isInitialized(programmingExercise.getTemplateParticipation())) {
                        final var programmingExerciseReloaded = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                        programmingExercise.setTemplateParticipation(programmingExerciseReloaded.getTemplateParticipation());
                    }
                    // this will also create initial (empty) submissions for quiz, text, modeling and file upload
                    StudentParticipation participation = participationService.startExercise(exercise, student, true);

                    generatedParticipations.add(participation);
                    // Unlock repository and participation only if the real exam starts within 5 minutes or if we have a test exam or test run
                    if (participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation && exercise instanceof ProgrammingExercise programmingExercise) {
                        if (studentExam.isTestRun() || studentExam.isTestExam()
                                || ExamDateService.getExamProgrammingExerciseUnlockDate(programmingExercise).isBefore(ZonedDateTime.now())) {
                            // Note: only unlock the programming exercise student repository for the affected user (Important: Do NOT invoke unlockAll)
                            programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation(programmingParticipation);
                        }
                        else {
                            programmingExerciseParticipationService.lockStudentParticipation(programmingParticipation);
                        }
                    }
                    log.info("SUCCESS: Start exercise for student exam {} and exercise {} and student {}", studentExam.getId(), exercise.getId(),
                            student.getParticipantIdentifier());
                }
                catch (Exception ex) {
                    log.warn("FAILED: Start exercise for student exam {} and exercise {} and student {} with exception: {}", studentExam.getId(), exercise.getId(),
                            student.getParticipantIdentifier(), ex.getMessage(), ex);
                }
            }
        }
        if (!generatedParticipations.isEmpty()) {
            studentExam.setStudentParticipations(generatedParticipations);
            this.studentExamRepository.save(studentExam);
        }
    }

    /**
     * Starts all the exercises of all the student exams of an exam
     *
     * @param examId exam to which the student exams belong
     * @return a future that will yield the number of generated participations
     */
    public CompletableFuture<Integer> startExercises(Long examId) {
        var exam = examRepository.findWithStudentExamsExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
        var studentExams = exam.getStudentExams();
        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());

        var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
        if (cache != null) {
            cache.evict(examId);
        }

        var finishedExamsCounter = new AtomicInteger(0);
        var failedExamsCounter = new AtomicInteger(0);
        var startedAt = ZonedDateTime.now();
        var lock = new ReentrantLock();
        sendAndCacheExercisePreparationStatus(examId, 0, 0, studentExams.size(), 0, startedAt, lock);

        var threadPool = Executors.newFixedThreadPool(10);
        var futures = studentExams.stream()
                .map(studentExam -> CompletableFuture.runAsync(() -> setUpExerciseParticipationsAndSubmissions(studentExam, generatedParticipations), threadPool)
                        .thenRun(() -> sendAndCacheExercisePreparationStatus(examId, finishedExamsCounter.incrementAndGet(), failedExamsCounter.get(), studentExams.size(),
                                generatedParticipations.size(), startedAt, lock))
                        .exceptionally(throwable -> {
                            log.error("Exception while preparing exercises for student exam {}", studentExam.getId(), throwable);
                            sendAndCacheExercisePreparationStatus(examId, finishedExamsCounter.get(), failedExamsCounter.incrementAndGet(), studentExams.size(),
                                    generatedParticipations.size(), startedAt, lock);
                            return null;
                        }))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply((emtpy) -> {
            threadPool.shutdown();
            sendAndCacheExercisePreparationStatus(examId, finishedExamsCounter.get(), failedExamsCounter.get(), studentExams.size(), generatedParticipations.size(), startedAt,
                    lock);
            return generatedParticipations.size();
        });
    }

    private void sendAndCacheExercisePreparationStatus(Long examId, int finished, int failed, int overall, int participations, ZonedDateTime startTime, ReentrantLock lock) {
        // Synchronizing and comparing to avoid race conditions here
        // Otherwise it can happen that a status with less completed exams is sent after one with a higher value
        try {
            lock.lock();
            ExamExerciseStartPreparationStatus status = null;
            var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
            if (cache != null) {
                var oldValue = cache.get(examId);
                if (oldValue != null) {
                    var oldStatus = (ExamExerciseStartPreparationStatus) oldValue.get();
                    if (oldStatus != null) {
                        status = new ExamExerciseStartPreparationStatus(Math.max(finished, oldStatus.finished()), Math.max(failed, oldStatus.failed()),
                                Math.max(overall, oldStatus.overall()), Math.max(participations, oldStatus.participationCount()), startTime);
                    }
                }
                if (status == null) {
                    status = new ExamExerciseStartPreparationStatus(finished, failed, overall, participations, startTime);
                }
                cache.put(examId, status);
            }
            else {
                log.warn("Unable to add exam exercise start status to distributed cache because it is null");
            }
            websocketMessagingService.sendMessage(EXAM_EXERCISE_START_STATUS_TOPIC.formatted(examId), status);
        }
        catch (Exception e) {
            log.warn("Failed to send exercise preparation status", e);
        }
        finally {
            lock.unlock();
        }
    }

    public Optional<ExamExerciseStartPreparationStatus> getExerciseStartStatusOfExam(Long examId) {
        return Optional.ofNullable(cacheManager.getCache(EXAM_EXERCISE_START_STATUS)).map(cache -> cache.get(examId))
                .map(wrapper -> (ExamExerciseStartPreparationStatus) wrapper.get());
    }

    /**
     * Generates a new individual StudentExam for the specified student and stores it in the database.
     *
     * @param exam    The exam with eagerly loaded users, exercise groups, and exercises.
     * @param student The student for whom the StudentExam should be created.
     * @return The generated StudentExam.
     */
    public StudentExam generateIndividualStudentExam(Exam exam, User student) {
        // To create a new StudentExam, the Exam with loaded ExerciseGroups and Exercises is needed
        long start = System.nanoTime();
        Set<User> userSet = Collections.singleton(student);
        StudentExam studentExam = studentExamRepository.createRandomStudentExams(exam, userSet, examQuizQuestionsGenerator).getFirst();
        // we need to break a cycle for the serialization
        studentExam.getExam().setExerciseGroups(null);
        studentExam.getExam().setStudentExams(null);

        log.info("Generated 1 student exam for {} in {} for exam {}", student.getId(), formatDurationFrom(start), exam.getId());

        return studentExam;
    }

    /**
     * Generates the student exams randomly based on the exam configuration and the exercise groups
     * Important: the passed exams needs to include the registered users, exercise groups and exercises (eagerly loaded)
     *
     * @param exam with eagerly loaded registered users, exerciseGroups and exercises loaded
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateStudentExams(final Exam exam) {
        final var existingStudentExams = studentExamRepository.findByExamId(exam.getId());
        // deleteInBatch does not work, because it does not cascade the deletion of existing exam sessions, therefore use deleteAll
        studentExamRepository.deleteAll(existingStudentExams);

        Set<User> users = exam.getRegisteredUsers();

        // StudentExams are saved in the called method
        return studentExamRepository.createRandomStudentExams(exam, users, examQuizQuestionsGenerator);
    }

    /**
     * Generates the missing student exams randomly based on the exam configuration and the exercise groups.
     * The difference between all registered users and the users who already have an individual exam is the set of users for which student exams will be created.
     * <p>
     * Important: the passed exams needs to include the registered users, exercise groups and exercises (eagerly loaded)
     *
     * @param exam with eagerly loaded registered users, exerciseGroups and exercises loaded
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateMissingStudentExams(Exam exam) {

        // Get all users who already have an individual exam
        Set<User> usersWithStudentExam = studentExamRepository.findUsersWithStudentExamsForExam(exam.getId());

        // Get all students who don't have an exam yet
        Set<User> missingUsers = exam.getRegisteredUsers();
        missingUsers.removeAll(usersWithStudentExam);

        // StudentExams are saved in the called method
        return studentExamRepository.createRandomStudentExams(exam, missingUsers, examQuizQuestionsGenerator);
    }
}
