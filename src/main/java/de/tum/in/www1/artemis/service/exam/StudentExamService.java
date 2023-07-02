package de.tum.in.www1.artemis.service.exam;

import static de.tum.in.www1.artemis.config.Constants.EXAM_EXERCISE_START_STATUS;
import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.SubmissionVersionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.service.util.ExamExerciseStartPreparationStatus;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing StudentExam.
 */
@Service
public class StudentExamService {

    private static final String EXAM_EXERCISE_START_STATUS_TOPIC = "/topic/exams/%s/exercise-start-status";

    private static final String WORKING_TIME_CHANGE_DURING_CONDUCTION_TOPIC = "/topic/studentExams/%s/working-time-change-during-conduction";

    private final Logger log = LoggerFactory.getLogger(StudentExamService.class);

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

    private final TextSubmissionRepository textSubmissionRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExamRepository examRepository;

    private final CacheManager cacheManager;

    private final SimpMessageSendingOperations messagingTemplate;

    private final TaskScheduler scheduler;

    public StudentExamService(StudentExamRepository studentExamRepository, UserRepository userRepository, ParticipationService participationService,
            QuizSubmissionRepository quizSubmissionRepository, TextSubmissionRepository textSubmissionRepository, ModelingSubmissionRepository modelingSubmissionRepository,
            SubmissionVersionService submissionVersionService, ProgrammingExerciseParticipationService programmingExerciseParticipationService, SubmissionService submissionService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, StudentParticipationRepository studentParticipationRepository, ExamQuizService examQuizService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingTriggerService programmingTriggerService, ExamRepository examRepository,
            CacheManager cacheManager, SimpMessageSendingOperations messagingTemplate, @Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.participationService = participationService;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.submissionVersionService = submissionVersionService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.examQuizService = examQuizService;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.examRepository = examRepository;
        this.cacheManager = cacheManager;
        this.messagingTemplate = messagingTemplate;
        this.scheduler = scheduler;
    }

    /**
     * Submit StudentExam and uses submissions as final submissions if studentExam is not yet submitted
     * and if it was submitted after exam startDate and before individual endDate + gracePeriod
     *
     * @param existingStudentExam the existing student exam object in the database
     * @param studentExam         the student exam object from the client which will be submitted (final submission)
     * @param currentUser         the current user
     * @return ResponseEntity.ok() on success or HTTP error with a custom error message on failure
     */
    public ResponseEntity<StudentExam> submitStudentExam(StudentExam existingStudentExam, StudentExam studentExam, User currentUser) {
        log.debug("Submit student exam with id {}", studentExam.getId());

        // most important aspect here: set studentExam to submitted and set submission date
        submitStudentExam(studentExam);

        try {
            // in case there were last second changes, that have not been submitted yet.
            saveSubmissions(studentExam, currentUser);
        }
        catch (Exception e) {
            log.error("saveSubmissions threw an exception", e);
        }

        // NOTE: only for real exams and test exams, the student repositories need to be locked
        // For test runs, this is not needed, because instructors have admin permissions on the VCS project (which contains the repository) anyway
        if (!studentExam.isTestRun()) {
            try {
                // lock the programming exercise repository access (important in case of early exam submissions)
                lockStudentRepositories(currentUser, existingStudentExam);
            }
            catch (Exception e) {
                log.error("lockStudentRepositories threw an exception", e);
            }
        }

        // NOTE: only for test runs and test exams, the quizzes should be evaluated automatically
        if (studentExam.isTestRun() || studentExam.isTestExam()) {
            // immediately evaluate quiz participations for test runs and test exams
            examQuizService.evaluateQuizParticipationsForTestRunAndTestExam(studentExam);

            // Trigger build for all programing participations
            var currentStudentParticipations = studentExam.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise)
                    .flatMap(exercise -> studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerLegalSubmissions(exercise.getId(), currentUser.getId()).stream())
                    .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();

            if (!currentStudentParticipations.isEmpty()) {
                // Delay to ensure that "Building and testing" is shown in the client
                scheduler.schedule(() -> programmingTriggerService.triggerBuildForParticipations(currentStudentParticipations), Instant.now().plus(3, ChronoUnit.SECONDS));
            }
        }

        return ResponseEntity.ok(studentExam);
    }

    private void submitStudentExam(StudentExam studentExam) {
        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.save(studentExam);
    }

    private void saveSubmissions(StudentExam studentExam, User currentUser) {
        List<StudentParticipation> existingParticipations = studentParticipationRepository.findByStudentExamWithEagerSubmissions(studentExam);

        for (Exercise exercise : studentExam.getExercises()) {
            // we do not apply the following checks for programming exercises or file upload exercises
            try {
                saveSubmission(currentUser, existingParticipations, exercise);
            }
            catch (Exception e) {
                log.error("saveSubmission threw an exception", e);
            }
        }
    }

    private void saveSubmission(User currentUser, List<StudentParticipation> existingParticipations, Exercise exercise) {
        if (exercise instanceof ProgrammingExercise) {
            // there is an edge case in which the student exam does not contain the latest programming submission (e.g. when the user was offline in between)
            // we fetch the latest programming submission from the DB here and replace it in the participation of the exercise so that the latest one will be returned below
            try {
                if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
                    var studentParticipation = exercise.getStudentParticipations().iterator().next();
                    var latestSubmission = programmingSubmissionRepository.findLatestLegalSubmissionForParticipation(studentParticipation.getId(), PageRequest.of(0, 1)).stream()
                            .findFirst();
                    latestSubmission.ifPresent(programmingSubmission -> studentParticipation.setSubmissions(Set.of(programmingSubmission)));
                }
            }
            catch (Exception ex) {
                log.error("An error occurred when trying to find the latest submissions for programming exercise {} for user {}", exercise.getId(), currentUser.getLogin());
            }
            return;
        }
        if (exercise instanceof FileUploadExercise) {
            // file upload submissions are only saved during submit in their respective submission page
            return;
        }

        // if exercise is either QuizExercise, TextExercise or ModelingExercise and exactly one participation exists
        if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                StudentParticipation existingParticipation = existingParticipations.stream().filter(p -> p.getId().equals(studentParticipation.getId())).findFirst().orElseThrow();
                // if exactly one submission exists we save the submission
                if (studentParticipation.getSubmissions() != null && studentParticipation.getSubmissions().size() == 1) {
                    // check that the current user owns the participation
                    if (!studentParticipation.isOwnedBy(currentUser) || !existingParticipation.isOwnedBy(currentUser)) {
                        throw new AccessForbiddenException("User " + currentUser.getLogin() + " is not allowed to access the participation " + existingParticipation.getId());
                    }
                    studentParticipation.setExercise(exercise);
                    for (Submission submission : studentParticipation.getSubmissions()) {

                        // check that the submission belongs to the already saved participation
                        if (!existingParticipation.getSubmissions().contains(submission)) {
                            throw new AccessForbiddenException("User " + currentUser.getLogin() + " cannot submit a different submission " + submission + " for participation "
                                    + existingParticipation.getId());
                        }
                        // check that no result has been injected
                        if (submission.getLatestResult() != null) {
                            throw new AccessForbiddenException("User " + currentUser.getLogin() + " cannot inject a result " + submission.getLatestResult() + " for submission "
                                    + submission + " and participation " + existingParticipation.getId());
                        }
                        submission.setParticipation(studentParticipation);
                        submission.submissionDate(ZonedDateTime.now());
                        submission.submitted(true);
                        if (exercise instanceof QuizExercise) {
                            // recreate pointers back to submission in each submitted answer
                            for (SubmittedAnswer submittedAnswer : ((QuizSubmission) submission).getSubmittedAnswers()) {
                                submittedAnswer.setSubmission(((QuizSubmission) submission));
                                if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                                    ((DragAndDropSubmittedAnswer) submittedAnswer).getMappings()
                                            .forEach(dragAndDropMapping -> dragAndDropMapping.setSubmittedAnswer(((DragAndDropSubmittedAnswer) submittedAnswer)));
                                }
                                else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                                    ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts()
                                            .forEach(submittedText -> submittedText.setSubmittedAnswer(((ShortAnswerSubmittedAnswer) submittedAnswer)));
                                }
                            }
                            quizSubmissionRepository.save((QuizSubmission) submission);
                        }
                        else if (exercise instanceof TextExercise) {
                            textSubmissionRepository.save((TextSubmission) submission);
                        }
                        else if (exercise instanceof ModelingExercise) {
                            modelingSubmissionRepository.save((ModelingSubmission) submission);
                        }

                        // versioning of submission
                        try {
                            submissionVersionService.saveVersionForIndividual(submission, currentUser);
                        }
                        catch (Exception ex) {
                            log.error("Submission version could not be saved", ex);
                        }
                    }
                }
            }
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

    private void lockStudentRepositories(User currentUser, StudentExam studentExam) {
        // Only lock programming exercises when the student submitted early in real exams. Otherwise, the lock operations were already scheduled/executed.
        // Always lock test exams since there is no locking operation scheduled (also see StudentExamService:457)
        if (studentExam.isTestExam() || (studentExam.getIndividualEndDate() != null && ZonedDateTime.now().isBefore(studentExam.getIndividualEndDate()))) {
            // Use the programming exercises in the DB to lock the repositories (for safety)
            for (Exercise exercise : studentExam.getExercises()) {
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    try {
                        log.debug("lock student repositories for {}", currentUser);
                        var participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentId(programmingExercise, currentUser.getLogin());
                        programmingExerciseParticipationService.lockStudentRepositoryAndParticipation(programmingExercise, participation);
                    }
                    catch (Exception e) {
                        log.error("Locking programming exercise {} submitted manually by {} failed", exercise.getId(), currentUser.getLogin(), e);
                    }
                }
            }
        }
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
     * @param startedDate the Date to which the InitializationDate should be set, in order to link StudentExam <-> participation
     */
    public void setUpTestExamExerciseParticipationsAndSubmissions(StudentExam studentExam, ZonedDateTime startedDate) {
        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());
        setUpExerciseParticipationsAndSubmissionsWithInitializationDate(studentExam, generatedParticipations, startedDate);
        // TODO: Michael Allgaier: schedule a lock operation for all involved student repositories of this student exam (test exam) at the end of the individual working time
        studentParticipationRepository.saveAll(generatedParticipations);
    }

    /**
     * Helper-Method for the Set up process of an StudentExam with a given startedDate. The method forces a new participation for every exercise,
     * unlocks the Repository in case the StudentExam starts in less than 5mins and returns the generated participations
     *
     * @param studentExam             the studentExam for which the new participations should be set up
     * @param generatedParticipations the list where the newly generated participations should be added
     * @param startedDate             the Date to which the InitializationDate should be set, in order to link StudentExam <-> participation
     */
    private void setUpExerciseParticipationsAndSubmissionsWithInitializationDate(StudentExam studentExam, List<StudentParticipation> generatedParticipations,
            ZonedDateTime startedDate) {
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
            if (studentParticipations.stream().noneMatch(studentParticipation -> studentParticipation.getParticipant().equals(student)
                    && studentParticipation.getInitializationState() != null && studentParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                try {
                    // Load lazy property
                    if (exercise instanceof ProgrammingExercise programmingExercise && !Hibernate.isInitialized(programmingExercise.getTemplateParticipation())) {
                        final var programmingExerciseReloaded = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                        programmingExercise.setTemplateParticipation(programmingExerciseReloaded.getTemplateParticipation());
                    }
                    // this will also create initial (empty) submissions for quiz, text, modeling and file upload
                    // If the startedDate is provided, the InitializationDate is set to the startedDate
                    StudentParticipation participation;
                    if (startedDate != null) {
                        participation = participationService.startExerciseWithInitializationDate(exercise, student, true, startedDate);
                    }
                    else {
                        participation = participationService.startExercise(exercise, student, true);
                    }
                    generatedParticipations.add(participation);
                    // Unlock repository and participation only if the real exam starts within 5 minutes or if we have a test exam or test run
                    if (participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation && exercise instanceof ProgrammingExercise programmingExercise) {
                        if (studentExam.isTestRun() || studentExam.isTestExam()
                                || ProgrammingExerciseScheduleService.getExamProgrammingExerciseUnlockDate(programmingExercise).isBefore(ZonedDateTime.now())) {
                            // Note: only unlock the programming exercise student repository for the affected user (Important: Do NOT invoke unlockAll)
                            programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation(programmingExercise, programmingParticipation);
                        }
                        else {
                            programmingExerciseParticipationService.lockStudentParticipation(programmingExercise, programmingParticipation);
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
                .toList().toArray(new CompletableFuture<?>[studentExams.size()]);
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
            messagingTemplate.convertAndSend(EXAM_EXERCISE_START_STATUS_TOPIC.formatted(examId), status);
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
     * Sets up the participations and submissions for all the exercises of the student exam.
     *
     * @param studentExam             The studentExam for which the participations and submissions should be created
     * @param generatedParticipations List of generated participations to track how many participations have been generated
     */
    public void setUpExerciseParticipationsAndSubmissions(StudentExam studentExam, List<StudentParticipation> generatedParticipations) {
        setUpExerciseParticipationsAndSubmissionsWithInitializationDate(studentExam, generatedParticipations, null);
    }

    /**
     * Generates a new test exam for the student and stores it in the database
     *
     * @param exam    the exam with loaded exercise groups and exercises for which the StudentExam should be created
     * @param student the corresponding student
     * @return a StudentExam for the student and exam
     */
    public StudentExam generateTestExam(Exam exam, User student) {
        // To create a new StudentExam, the Exam with loaded ExerciseGroups and Exercises is needed
        long start = System.nanoTime();
        StudentExam studentExam = generateIndividualStudentExam(exam, student);
        // we need to break a cycle for the serialization
        studentExam.getExam().setExerciseGroups(null);
        studentExam.getExam().setStudentExams(null);

        log.info("Generated 1 student exam for {} in {} for exam {}", student.getId(), formatDurationFrom(start), exam.getId());

        return studentExam;
    }

    /**
     * Generates an individual StudentExam
     *
     * @param exam    with eagerly loaded users, exerciseGroups and exercises loaded
     * @param student the student for which the StudentExam should be created
     * @return the generated StudentExam
     */
    private StudentExam generateIndividualStudentExam(Exam exam, User student) {
        // StudentExams are saved in the called method
        HashSet<User> userHashSet = new HashSet<>();
        userHashSet.add(student);
        return studentExamRepository.createRandomStudentExams(exam, userHashSet).get(0);
    }

    public void notifyStudentAboutWorkingTimeChangeDuringConduction(StudentExam studentExam) {
        messagingTemplate.convertAndSend(WORKING_TIME_CHANGE_DURING_CONDUCTION_TOPIC.formatted(studentExam.getId()), studentExam.getWorkingTime());
    }
}
