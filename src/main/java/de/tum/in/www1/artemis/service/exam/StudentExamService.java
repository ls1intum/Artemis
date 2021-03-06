package de.tum.in.www1.artemis.service.exam;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
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
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing StudentExam.
 */
@Service
public class StudentExamService {

    private static final String ENTITY_NAME = "studentExam";

    private final Logger log = LoggerFactory.getLogger(StudentExamService.class);

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

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

    public StudentExamService(StudentExamRepository studentExamRepository, UserRepository userRepository, ParticipationService participationService,
            QuizSubmissionRepository quizSubmissionRepository, TextSubmissionRepository textSubmissionRepository, ModelingSubmissionRepository modelingSubmissionRepository,
            SubmissionVersionService submissionVersionService, ProgrammingExerciseParticipationService programmingExerciseParticipationService, SubmissionService submissionService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, StudentParticipationRepository studentParticipationRepository, ExamQuizService examQuizService,
            ProgrammingExerciseRepository programmingExerciseRepository, ExamRepository examRepository) {
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
        this.examRepository = examRepository;
    }

    /**
     * Get one student exam by exam id and user.
     *
     * @param examId the id of the exam
     * @param userId the id of the user
     * @return the student exam with exercises
     */
    public Optional<StudentExam> findOneWithExercisesByUserIdAndExamId(Long userId, Long examId) {
        log.debug("Request to get student exam by userId {} and examId {}", userId, examId);
        return studentExamRepository.findWithExercisesByUserIdAndExamId(userId, examId);
    }

    /**
     * Get one optional student exam by exam id and user.
     *
     * @param examId the id of the exam
     * @param userId the id of the user
     * @return the student exam with exercises
     */
    @NotNull
    public Optional<StudentExam> findOneWithExercisesByUserIdAndExamIdOptional(Long userId, Long examId) {
        log.debug("Request to get optional student exam by userId {} and examId {}", userId, examId);
        return studentExamRepository.findWithExercisesByUserIdAndExamId(userId, examId);
    }

    /**
     * Submit StudentExam and uses submissions as final submissions if studentExam is not yet submitted
     * and if it was submitted after exam startDate and before individual endDate + gracePeriod
     *
     * @param existingStudentExam the existing student exam object in the database
     * @param studentExam the student exam object from the client which will be submitted (final submission)
     * @param currentUser the current user
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

        if (!studentExam.isTestRun()) {
            try {
                // lock the programming exercise repository access (important in case of early exam submissions)
                lockStudentRepositories(currentUser, existingStudentExam);
            }
            catch (Exception e) {
                log.error("lockStudentRepositories threw an exception", e);
            }
        }
        else {
            // immediately evaluate quiz participations for test runs
            examQuizService.evaluateQuizParticipationsForTestRun(studentExam);
        }

        return ResponseEntity.ok(studentExam);
    }

    private void submitStudentExam(StudentExam studentExam) {
        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.save(studentExam);
    }

    private void saveSubmissions(StudentExam studentExam, User currentUser) {
        List<StudentParticipation> existingParticipations = studentParticipationRepository.findByStudentExamWithEagerSubmissionsResult(studentExam, false);

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
                    var latestSubmission = programmingSubmissionRepository.findLatestSubmissionForParticipation(studentParticipation.getId(), PageRequest.of(0, 1)).stream()
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
            // TODO: remove
            return;
        }

        // if exercise is either QuizExercise, TextExercise or ModelingExercise and exactly one participation exists
        if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                StudentParticipation existingParticipation = existingParticipations.stream().filter(p -> p.getId().equals(studentParticipation.getId()))
                        .collect(Collectors.toList()).get(0);
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
                        // TODO: handle file upload

                        // versioning of submission
                        try {
                            submissionVersionService.saveVersionForIndividual(submission, currentUser.getLogin());
                        }
                        catch (Exception ex) {
                            log.error("Submission version could not be saved: " + ex);
                        }
                    }
                }
            }
        }
    }

    /**
     * Assess the modeling-, text and file upload exercises of student exams of an exam which are not submitted with 0 points.
     *
     * @param exam the exam
     * @param assessor the assessor should be the instructor making the call.
     * @return returns the set of unsubmitted StudentExams, the participations of which were assessed
     */
    public Set<StudentExam> assessUnsubmittedStudentExams(final Exam exam, final User assessor) {
        // TODO: we should also do this for programming exercises with manual assessment
        Set<StudentExam> unsubmittedStudentExams = findAllUnsubmittedStudentExams(exam.getId());
        Map<User, List<Exercise>> exercisesOfUser = unsubmittedStudentExams.stream()
                .collect(Collectors.toMap(StudentExam::getUser,
                        studentExam -> studentExam.getExercises().stream()
                                .filter(exercise -> exercise instanceof ModelingExercise || exercise instanceof TextExercise || exercise instanceof FileUploadExercise)
                                .collect(Collectors.toList())));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                final var latestSubmission = studentParticipation.findLatestSubmission();
                if (latestSubmission.isPresent()) {
                    for (int correctionRound = 0; correctionRound < exam.getNumberOfCorrectionRoundsInExam(); correctionRound++) {
                        // required so that the submission is counted in the assessment dashboard
                        latestSubmission.get().submitted(true);
                        submissionService.addResultWithFeedbackByCorrectionRound(studentParticipation, assessor, 0L, "You did not submit your exam", correctionRound);
                    }
                }
            }
        }
        return unsubmittedStudentExams;
    }

    /**
     * Assess the modeling-, file upload and text submissions of an exam which are empty.
     *
     * @param exam the exam
     * @param assessor the assessor should be the instructor making the call
     * @param excludeStudentExams studentExams which should be excluded. This is used to exclude unsubmitted student exams because they are already assessed, see {@link StudentExamService#assessUnsubmittedStudentExams}
     * @return returns the set of StudentExams of which the empty submissions were assessed
     */
    public Set<StudentExam> assessEmptySubmissionsOfStudentExams(final Exam exam, final User assessor, final Set<StudentExam> excludeStudentExams) {
        // TODO: we should also do this for programming exercises with manual assessment
        Set<StudentExam> studentExams = findAllWithExercisesByExamId(exam.getId());
        // remove student exams which should be excluded
        studentExams = studentExams.stream().filter(studentExam -> !excludeStudentExams.contains(studentExam)).collect(Collectors.toSet());
        Map<User, List<Exercise>> exercisesOfUser = studentExams.stream()
                .collect(Collectors.toMap(StudentExam::getUser,
                        studentExam -> studentExam.getExercises().stream()
                                .filter(exercise -> exercise instanceof ModelingExercise || exercise instanceof TextExercise || exercise instanceof FileUploadExercise)
                                .collect(Collectors.toList())));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                final var latestSubmission = studentParticipation.findLatestSubmission();
                if (latestSubmission.isPresent() && latestSubmission.get().isEmpty()) {
                    for (int correctionRound = 0; correctionRound < exam.getNumberOfCorrectionRoundsInExam(); correctionRound++) {
                        // required so that the submission is counted in the assessment dashboard
                        latestSubmission.get().submitted(true);
                        submissionService.addResultWithFeedbackByCorrectionRound(studentParticipation, assessor, 0L, "Empty submission", correctionRound);
                    }
                }
            }
        }
        return studentExams;
    }

    private void lockStudentRepositories(User currentUser, StudentExam existingStudentExam) {
        // Only lock programming exercises when the student submitted early. Otherwise, the lock operations were already scheduled/executed.
        if (existingStudentExam.getIndividualEndDate() != null && ZonedDateTime.now().isBefore(existingStudentExam.getIndividualEndDate())) {
            // Use the programming exercises in the DB to lock the repositories (for safety)
            for (Exercise exercise : existingStudentExam.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    try {
                        log.debug("lock student repositories for {}", currentUser);
                        ProgrammingExerciseStudentParticipation participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentId(exercise,
                                currentUser.getLogin());
                        programmingExerciseParticipationService.lockStudentRepository((ProgrammingExercise) exercise, participation);
                    }
                    catch (Exception e) {
                        log.error("Locking programming exercise " + exercise.getId() + " submitted manually by " + currentUser.getLogin() + " failed", e);
                    }
                }
            }
        }
    }

    /**
     * Get one student exam by id with exercises.
     *
     * @param studentExamId the id of the student exam
     * @return the student exam with exercises
     */
    @NotNull
    public StudentExam findOneWithExercises(Long studentExamId) {
        log.debug("Request to get student exam {} with exercises", studentExamId);
        return studentExamRepository.findWithExercisesById(studentExamId)
                .orElseThrow(() -> new EntityNotFoundException("Student exam with id \"" + studentExamId + "\" does not exist"));
    }

    /**
     * Get all student exams for the given exam.
     *
     * @param examId the id of the exam
     * @return the list of all student exams
     */
    public Set<StudentExam> findAllByExamId(Long examId) {
        log.debug("Request to get all student exams for Exam: {}", examId);
        return studentExamRepository.findByExamId(examId);
    }

    /**
     * Get all student exams with exercises for the given exam.
     *
     * @param examId the id of the exam
     * @return the list of all student exams
     */
    public Set<StudentExam> findAllWithExercisesByExamId(Long examId) {
        log.debug("Request to get all student exams with exercises for Exam: {}", examId);
        return studentExamRepository.findAllWithExercisesByExamId(examId);
    }

    /**
     * Delete a student exam by the Id
     *
     * @param studentExamId the id of the student exam to be deleted
     */
    public void deleteStudentExam(Long studentExamId) {
        log.debug("Request to delete the student exam with Id : {}", studentExamId);
        studentExamRepository.deleteById(studentExamId);
    }

    /**
     * Get the maximal working time of all student exams for the exam with the given id.
     *
     * @param examId the id of the exam
     * @return the maximum of all student exam working times for the given exam
     * @throws EntityNotFoundException if no student exams could be found
     */
    @NotNull
    public Integer findMaxWorkingTimeByExamId(Long examId) {
        log.debug("Request to get the maximum working time of all student exams for Exam : {}", examId);
        return studentExamRepository.findMaxWorkingTimeByExamId(examId).orElseThrow(() -> new EntityNotFoundException("No student exams found for exam id " + examId));
    }

    /**
     * Get all distinct student working times of one exam.
     *
     * @param examId the id of the exam
     * @return a set of all distinct working time values among the student exams of an exam. May be empty if no student exams can be found.
     */
    @NotNull
    public Set<Integer> findAllDistinctWorkingTimesByExamId(Long examId) {
        log.debug("Request to find all distinct working times for Exam : {}", examId);
        return studentExamRepository.findAllDistinctWorkingTimesByExamId(examId);
    }

    /**
     * Generates a Student Exam marked as a testRun for the instructor to test the exam as a student would experience it.
     * Calls {@link StudentExamService#generateTestRun and {@link ExamService#setUpTestRunExerciseParticipationsAndSubmissions}}
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
     *
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
     * Starts all the exercises of all the student exams of an exam
     *
     * @param examId exam to which the student exams belong
     * @return number of generated Participations
     */
    public int startExercises(Long examId) {
        var exam = examRepository.findWithStudentExamsExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
        var studentExams = exam.getStudentExams();
        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());
        executeInParallel(() -> studentExams.parallelStream().forEach(studentExam -> setUpExerciseParticipationsAndSubmissions(studentExam, generatedParticipations)));
        return generatedParticipations.size();
    }

    private void executeInParallel(Runnable task) {
        final int numberOfParallelThreads = 10;
        ForkJoinPool forkJoinPool = new ForkJoinPool(numberOfParallelThreads);
        Future<?> future = forkJoinPool.submit(task);
        // Wait for the operation to complete
        try {
            future.get();
        }
        catch (InterruptedException e) {
            log.error("Execute in parallel got interrupted while waiting for task to complete", e);
        }
        catch (ExecutionException e) {
            log.error("Execute in parallel failed, an exception was thrown", e.getCause());
        }
        finally {
            forkJoinPool.shutdown();
        }
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
            SecurityUtils.setAuthorizationObject();
            // NOTE: it is not ideal to invoke the next line several times (e.g. 2000 student exams with 10 exercises would lead to 20.000 database calls to find a participation).
            // One optimization could be that we load all participations per exercise once (or per exercise) into a large list (10 * 2000 = 20.000 participations) and then check if
            // those participations exist in Java, however this might lead to memory issues and might be more difficult to program (and more difficult to understand)
            // TODO: directly check in the database if the entry exists for the student, exercise and InitializationState.INITIALIZED
            var studentParticipations = participationService.findByExerciseAndStudentId(exercise, student.getId());
            // we start the exercise if no participation was found that was already fully initialized
            if (studentParticipations.stream().noneMatch(studentParticipation -> studentParticipation.getParticipant().equals(student)
                    && studentParticipation.getInitializationState() != null && studentParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                try {
                    if (exercise instanceof ProgrammingExercise) {
                        // TODO: we should try to move this out of the for-loop into the method which calls this method.
                        // Load lazy property
                        final var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                        ((ProgrammingExercise) exercise).setTemplateParticipation(programmingExercise.getTemplateParticipation());
                    }
                    // this will also create initial (empty) submissions for quiz, text, modeling and file upload
                    var participation = participationService.startExercise(exercise, student, true);
                    generatedParticipations.add(participation);
                }
                catch (Exception ex) {
                    log.warn("Start exercise for student exam {} and exercise {} and student {} failed with exception: {}", studentExam.getId(), exercise.getId(), student.getId(),
                            ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * Find all unsubmitted student exams (ignores test runs) with exercises.
     * @param examId the exam id
     * @return a set of student exams with {@link StudentExam#isSubmitted()} false
     */
    public Set<StudentExam> findAllUnsubmittedStudentExams(Long examId) {
        return studentExamRepository.findAllUnsubmittedWithExercisesByExamId(examId);
    }

    /**
     * Deletes a test run.
     * In case the participation is not referenced by other test runs, the participation, submission, build plans and repositories are deleted as well.
     * @param testRunId the id of the test run
     * @return the deleted test run
     */
    public StudentExam deleteTestRun(Long testRunId) {
        var testRun = findOneWithExercises(testRunId);
        User instructor = testRun.getUser();
        var participations = studentParticipationRepository.findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(instructor.getId(),
                testRun.getExercises());
        testRun.getExercises().forEach(exercise -> {
            var relevantParticipation = exercise.findRelevantParticipation(participations);
            if (relevantParticipation != null) {
                exercise.setStudentParticipations(Set.of(relevantParticipation));
            }
            else {
                exercise.setStudentParticipations(new HashSet<>());
            }
        });

        List<StudentExam> otherTestRunsOfInstructor = findAllTestRunsWithExercisesForUser(testRun.getExam().getId(), instructor.getId()).stream()
                .filter(studentExam -> !studentExam.getId().equals(testRunId)).collect(Collectors.toList());

        // Delete the participations and submissions if no other test run references them
        if (otherTestRunsOfInstructor.isEmpty()) {
            // Delete participations and submissions
            for (final Exercise exercise : testRun.getExercises()) {
                participationService.delete(exercise.getStudentParticipations().iterator().next().getId(), true, true);
            }
        }
        else {
            // We cannot delete participations which are referenced by other test runs. (an instructor is free to create as many test runs as he likes)
            var testRunExercises = testRun.getExercises();
            // Collect all distinct exercises of other instructor test runs
            var allInstructorTestRunExercises = otherTestRunsOfInstructor.stream().flatMap(tr -> tr.getExercises().stream()).distinct().collect(Collectors.toList());
            // Collect exercises which are not referenced by other test runs. Their participations can be safely deleted
            var exercisesToBeDeleted = testRunExercises.stream().filter(exercise -> !allInstructorTestRunExercises.contains(exercise)).collect(Collectors.toList());

            for (final Exercise exercise : exercisesToBeDeleted) {
                participationService.delete(exercise.getStudentParticipations().iterator().next().getId(), true, true);
            }
        }

        // Delete the test run student exam
        studentExamRepository.deleteById(testRunId);
        return testRun;
    }

    /**
     * Returns all test runs for a given exam
     * @param examId the id of the exam in question
     * @return a list of the test run student exams
     */
    public List<StudentExam> findAllTestRuns(Long examId) {
        return studentExamRepository.findAllTestRunsByExamId(examId);
    }

    /**
     * Returns all test runs for a given exam initiated by the given instructor
     * @param examId the id of the exam in question
     * @param userId the id of the user
     * @return a list of the test run student exams
     */
    public List<StudentExam> findAllTestRunsWithExercisesForUser(Long examId, Long userId) {
        return studentExamRepository.findAllTestRunsWithExercisesByExamIdForUser(examId, userId);
    }
}
