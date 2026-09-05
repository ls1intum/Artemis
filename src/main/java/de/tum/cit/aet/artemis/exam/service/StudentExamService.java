package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_EXERCISE_START_STATUS;
import static de.tum.cit.aet.artemis.core.util.TimeLogUtil.formatDurationFrom;
import static de.tum.cit.aet.artemis.exam.service.ExamSubmissionService.isContentEqualTo;

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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.AthenaFeedbackUsageDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitStudentExamDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExamGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionVersionService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.api.ModelingFeedbackApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingSubmissionApi;
import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.text.api.TextFeedbackApi;
import de.tum.cit.aet.artemis.text.api.TextSubmissionApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Service Implementation for managing StudentExam.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class StudentExamService {

    private static final String EXAM_EXERCISE_START_STATUS_TOPIC = "/topic/exams/%s/exercise-start-status";

    private static final Logger log = LoggerFactory.getLogger(StudentExamService.class);

    private final ParticipationService participationService;

    private final ExamService examService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final SubmissionService submissionService;

    private final ExamQuizService examQuizService;

    private final SubmissionVersionService submissionVersionService;

    private final StudentExamRepository studentExamRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final Optional<TextSubmissionApi> textSubmissionApi;

    private final Optional<ModelingSubmissionApi> modelingSubmissionApi;

    private final Optional<TextFeedbackApi> textFeedbackApi;

    private final Optional<ModelingFeedbackApi> modelingFeedbackApi;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExamRepository examRepository;

    private final CacheManager cacheManager;

    private final WebsocketMessagingService websocketMessagingService;

    private final TaskScheduler scheduler;

    private final StudentExamSubmitMapper studentExamSubmitMapper;

    private final TransactionTemplate transactionTemplate;

    /**
     * Maximum number of Athena feedback requests a student may accumulate across all of their submitted test-exam
     * attempts for a given exam. Reuses the course-exercise cap so the two stay in sync.
     */
    @Value("${artemis.athena.allowed-feedback-requests:10}")
    private int allowedFeedbackRequests;

    public StudentExamService(StudentExamRepository studentExamRepository, UserRepository userRepository, ParticipationService participationService,
            QuizSubmissionRepository quizSubmissionRepository, SubmittedAnswerRepository submittedAnswerRepository, Optional<TextSubmissionApi> textSubmissionApi,
            Optional<ModelingSubmissionApi> modelingSubmissionApi, Optional<TextFeedbackApi> textFeedbackApi, Optional<ModelingFeedbackApi> modelingFeedbackApi,
            SubmissionVersionService submissionVersionService, SubmissionService submissionService, StudentParticipationRepository studentParticipationRepository,
            ExamQuizService examQuizService, ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingTriggerService programmingTriggerService,
            ExerciseRepository exerciseRepository, ExamRepository examRepository, CacheManager cacheManager, WebsocketMessagingService websocketMessagingService,
            @Qualifier("taskScheduler") TaskScheduler scheduler, ExamService examService, StudentExamSubmitMapper studentExamSubmitMapper,
            PlatformTransactionManager transactionManager) {
        this.participationService = participationService;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.textSubmissionApi = textSubmissionApi;
        this.modelingSubmissionApi = modelingSubmissionApi;
        this.textFeedbackApi = textFeedbackApi;
        this.modelingFeedbackApi = modelingFeedbackApi;
        this.submissionVersionService = submissionVersionService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.examQuizService = examQuizService;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.exerciseRepository = exerciseRepository;
        this.examRepository = examRepository;
        this.cacheManager = cacheManager;
        this.websocketMessagingService = websocketMessagingService;
        this.scheduler = scheduler;
        this.examService = examService;
        this.studentExamSubmitMapper = studentExamSubmitMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Reconstructs the transient submission graph from the slim client DTO onto the authoritative student exam and then
     * submits it. This is the single service entry point for the hand-in endpoint, so the controller does not need to
     * depend on the reconstruction mapper directly.
     * <p>
     * The per-exercise degrade semantics of the reconstruction are owned by {@link StudentExamSubmitMapper} and left
     * unchanged: a broken exercise drops only its own last-second changes while the hand-in still succeeds.
     *
     * @param existingStudentExam  the student exam loaded from the database (with its exercises)
     * @param submitStudentExamDTO the slim request body carrying the last-second submission changes
     * @param currentUser          the current user
     */
    public void submitStudentExam(StudentExam existingStudentExam, SubmitStudentExamDTO submitStudentExamDTO, User currentUser) {
        studentExamSubmitMapper.attachSubmissions(existingStudentExam, submitStudentExamDTO, currentUser);
        submitStudentExam(existingStudentExam, currentUser);
    }

    /**
     * Submit StudentExam and uses submissions as final submissions if studentExam is not yet submitted
     * and if it was submitted after exam startDate and before individual endDate + gracePeriod
     *
     * @param studentExamFromClient the student exam object from the client which will be submitted (final submission)
     * @param currentUser           the current user
     */
    public void submitStudentExam(StudentExam studentExamFromClient, User currentUser) {
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

        // NOTE: from here on, we only handle test runs and test exams
        if (!studentExamFromClient.isTestRun() && !studentExamFromClient.isTestExam()) {
            return;
        }

        // NOTE: only for test runs and test exams, the quizzes should be evaluated automatically
        // immediately evaluate quiz participations for test runs and test exams
        examQuizService.evaluateQuizParticipationsForTestRunAndTestExam(studentExamFromClient);

        // Trigger build for all programing participations
        var currentStudentParticipations = studentExamFromClient.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise)
                .flatMap(exercise -> studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissions(exercise.getId(), currentUser.getId()).stream())
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();

        if (!currentStudentParticipations.isEmpty()) {
            // Delay to ensure that "Building and testing" is shown in the client
            scheduler.schedule(() -> programmingTriggerService.triggerBuildForParticipations(currentStudentParticipations), Instant.now().plus(3, ChronoUnit.SECONDS));
        }
    }

    /**
     * Requests Athena AI feedback for all text and modeling participations of a submitted test exam whose exercise
     * has a feedback suggestion module configured. Called explicitly by the student via the test exam summary button.
     * <p>
     * Rejects the request if the student has already accumulated {@link #allowedFeedbackRequests} successful Athena
     * results across all of their test-exam attempts for this exam (cross-attempt cap), or if no exercise in the
     * attempt has a feedback suggestion module configured. Individual submissions that already have an Athena result
     * are skipped silently inside the async dispatch in {@code generateAutomaticFeedbackForTestExamAsync}, so
     * remaining unassessed submissions in the same attempt still get processed.
     *
     * @param studentExam the submitted student exam
     * @param currentUser the user requesting feedback
     * @throws BadRequestAlertException if the exam is not a test exam, not submitted, Athena is unavailable, the
     *                                      request limit is reached, or no exercise has a feedback suggestion module
     *                                      configured
     */
    public void requestAthenaFeedbackForTestExam(StudentExam studentExam, User currentUser) {
        if (!Boolean.TRUE.equals(studentExam.isSubmitted())) {
            throw new BadRequestAlertException("Student exam must be submitted before requesting feedback", "StudentExam", "studentExamNotSubmitted");
        }
        if (!studentExam.isTestExam()) {
            throw new BadRequestAlertException("Athena feedback is only available for test exams", "StudentExam", "notTestExam");
        }
        if (textFeedbackApi.isEmpty() && modelingFeedbackApi.isEmpty()) {
            throw new BadRequestAlertException("Athena feedback is not available", "StudentExam", "athenaNotAvailable");
        }

        // Approximate cap: count-and-dispatch is not transactional, so concurrent requests at used == cap - 1 can both pass and briefly exceed the cap by one.
        long attemptsWithAthenaResult = studentExamRepository.countTestExamAttemptsWithAthenaResultByUserIdAndExamId(currentUser.getId(), studentExam.getExam().getId());
        if (attemptsWithAthenaResult >= allowedFeedbackRequests) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "StudentExam", "maxAthenaResultsReached", true);
        }

        List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(studentExam, false);
        List<StudentParticipation> eligibleParticipations = participations.stream()
                .filter(participation -> participation.getExercise() != null && participation.getExercise().getFeedbackSuggestionModule() != null).toList();
        if (eligibleParticipations.isEmpty()) {
            throw new BadRequestAlertException("No exam exercises with a configured AI feedback module", "StudentExam", "noFeedbackSuggestionModuleConfigured", true);
        }
        for (StudentParticipation participation : eligibleParticipations) {
            Exercise exercise = participation.getExercise();
            if (exercise instanceof TextExercise && textFeedbackApi.isEmpty()) {
                throw new BadRequestAlertException("Athena feedback for text exercises is not available", "StudentExam", "textAthenaNotAvailable");
            }
            if (exercise instanceof ModelingExercise && modelingFeedbackApi.isEmpty()) {
                throw new BadRequestAlertException("Athena feedback for modeling exercises is not available", "StudentExam", "modelingAthenaNotAvailable");
            }
        }
        for (StudentParticipation participation : eligibleParticipations) {
            Exercise exercise = participation.getExercise();
            if (exercise instanceof TextExercise textExercise) {
                textFeedbackApi.ifPresent(api -> api.generateAutomaticFeedbackForTestExamAsync(participation, textExercise));
            }
            else if (exercise instanceof ModelingExercise modelingExercise) {
                modelingFeedbackApi.ifPresent(api -> api.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise));
            }
        }
    }

    /**
     * Returns how many test-exam attempts of the given user have produced a successful Athena feedback result, paired
     * with the configured cap. Each attempt counts as one request regardless of how many exercises it contains.
     *
     * @param userId the id of the student whose test-exam attempts should be counted
     * @param examId the id of the exam the attempts belong to
     * @return the number of attempts that already produced an Athena result and the configured cap
     */
    public AthenaFeedbackUsageDTO getAthenaFeedbackUsage(Long userId, Long examId) {
        long used = studentExamRepository.countTestExamAttemptsWithAthenaResultByUserIdAndExamId(userId, examId);
        return new AthenaFeedbackUsageDTO(used, allowedFeedbackRequests);
    }

    private void submitStudentExam(StudentExam studentExam) {
        var now = ZonedDateTime.now();
        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(now);
        studentExamRepository.submitStudentExam(studentExam.getId(), now);
    }

    private void saveSubmissions(StudentExam studentExam, User currentUser) {
        // StudentExam.exercises is an @OrderColumn list, so Hibernate materializes a null for every gap in
        // exercise_order. A null passes an `instanceof` filter, so without the explicit null check the gap would reach
        // the participation query below (which is not covered by the per-exercise catch) and cost every exercise its
        // last-second changes.
        var exercises = studentExam.getExercises().stream().filter(Objects::nonNull).toList();
        // we only need to save submissions for modeling, text and quiz exercises;
        var relevantExercises = exercises.stream().filter(ex -> !(ex instanceof ProgrammingExercise) && !(ex instanceof FileUploadExercise)).toList();
        if (relevantExercises.isEmpty()) {
            // nothing to save
            return;
        }
        // 4. DB Call: read
        List<StudentParticipation> existingRelevantParticipations = studentParticipationRepository.findByStudentExamWithEagerSubmissions(studentExam, relevantExercises);

        for (Exercise exercise : exercises) {
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
        if (exercise.getStudentParticipations() == null || exercise.getStudentParticipations().size() != 1) {
            return;
        }

        // this object comes from the client
        StudentParticipation studentParticipationFromClient = exercise.getStudentParticipations().iterator().next();
        // this object comes from the database
        StudentParticipation existingParticipationInDatabase = existingRelevantParticipations.stream().filter(p -> p.getId().equals(studentParticipationFromClient.getId()))
                .findFirst().orElseThrow();

        // if exactly one submission exists we save the submission
        if (studentParticipationFromClient.getSubmissions() == null || studentParticipationFromClient.getSubmissions().size() != 1) {
            return;
        }

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
        switch (exercise) {
            case QuizExercise ignored -> saveSubmissionQuizExercise(currentUser, existingParticipationInDatabase, submissionFromClient);
            case TextExercise ignored -> saveSubmissionTextExercise(currentUser, existingParticipationInDatabase, submissionFromClient);
            case ModelingExercise ignored -> saveSubmissionModelingExercise(currentUser, existingParticipationInDatabase, submissionFromClient);
            default -> {
            }
        }
    }

    private void saveSubmissionModelingExercise(User currentUser, StudentParticipation existingParticipationInDatabase, Submission submissionFromClient) {
        ModelingSubmission existingSubmissionInDatabase = (ModelingSubmission) existingParticipationInDatabase.findLatestSubmission().orElse(null);
        ModelingSubmission modelingSubmissionFromClient = (ModelingSubmission) submissionFromClient;
        if (!isContentEqualTo(existingSubmissionInDatabase, modelingSubmissionFromClient)) {
            modelingSubmissionApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingSubmissionApi.class)).save(modelingSubmissionFromClient);
            saveSubmissionVersion(currentUser, submissionFromClient);
        }
    }

    private void saveSubmissionTextExercise(User currentUser, StudentParticipation existingParticipationInDatabase, Submission submissionFromClient) {
        TextSubmission existingSubmissionInDatabase = (TextSubmission) existingParticipationInDatabase.findLatestSubmission().orElse(null);
        TextSubmission textSubmissionFromClient = (TextSubmission) submissionFromClient;
        if (!isContentEqualTo(existingSubmissionInDatabase, textSubmissionFromClient)) {
            textSubmissionApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionApi.class)).saveTextSubmission(textSubmissionFromClient);
            saveSubmissionVersion(currentUser, submissionFromClient);
        }
    }

    private void saveSubmissionQuizExercise(User currentUser, StudentParticipation existingParticipationInDatabase, Submission submissionFromClient) {
        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : ((QuizSubmission) submissionFromClient).getSubmittedAnswers()) {
            submittedAnswer.setSubmission(((QuizSubmission) submissionFromClient));

            // Drag-and-drop submitted mappings are stored id-based in the JSON selection and need no back-reference fixup.
            if (submittedAnswer instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
                shortAnswerSubmittedAnswer.getSubmittedTexts().forEach(submittedText -> submittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer));
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

    private void saveSubmissionVersion(User currentUser, Submission submissionFromClient) {
        // Versioning of the submission, off the request thread. A version is a full copy of the submission content and
        // nothing in this request reads it back, so making the student wait for that write buys nothing. It was the
        // slowest statement in the submit path.
        try {
            submissionVersionService.saveVersionForIndividualAsync(submissionFromClient, currentUser);
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
            // fetch all studentParticipations of a user, with latest submission and results eagerly loaded
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(user.getId(),
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
     * Get the StudentExamWithGradeDTO for the given studentExam.
     *
     * @param examId        the examId of the studentExam
     * @param studentExamId the studentExamId for which the StudentExamWithGradeDTO should be calculated
     * @return the StudentExamWithGradeDTO for the given studentExam
     */
    public StudentExamWithGradeDTO getStudentExamWithGradeDTO(long examId, long studentExamId) {
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesAndSessionsAndStudentParticipationsElseThrow(studentExamId);
        examService.loadQuizExercisesForStudentExam(studentExam);
        // fetch participations, latest submissions and results for these exercises, note: exams only contain individual exercises for now
        // fetching all participations at once is more effective
        List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(studentExam, true);
        // fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);

        // connect the exercises and student participations correctly and make sure all relevant associations are available
        for (Exercise exercise : studentExam.getExercises()) {
            // add participation with submission and result to each exercise
            examService.filterParticipationForExercise(studentExam, exercise, participations, true);
        }

        studentExam.getUser().setVisibleRegistrationNumber();
        Set<ExamGradeScoreDTO> examGrades;
        if (studentExam.isTestRun()) {
            examGrades = studentParticipationRepository.findGradesByExamIdAndStudentIdForTestRun(examId, studentExam.getUser().getId());
        }
        else {
            examGrades = studentParticipationRepository.findGradesByExamIdAndStudentId(examId, studentExam.getUser().getId());
        }
        return examService.calculateStudentResultWithGradeAndPoints(studentExam, examGrades);
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
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (var studentParticipation : studentParticipations) {
                // even if the student did not submit anything for a specific exercise (the InitializationState is therefore only INITIALIZED)
                // we want to set it to FINISHED as the exam was handed in.
                if (studentParticipation.getInitializationState().equals(InitializationState.INITIALIZED)) {
                    studentParticipation.setInitializationState(InitializationState.FINISHED);
                    studentParticipationRepository.save(studentParticipation);
                }
                Optional<Submission> latestSubmission = studentParticipation.getSubmissions().stream().findFirst();
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
     * Resolves the exercise ids, then calls {@link StudentExamService#generateTestRun} and {@link StudentExamService#setUpTestRunExerciseParticipationsAndSubmissions}
     * <p>
     * Resolution and save share the exam-row lock the random generation paths take, so a concurrent exercise-group
     * move cannot commit between reading the exercises and persisting the selection. The participation setup runs
     * afterwards: it only needs the persisted selection and would hold the lock for the length of the setup.
     *
     * @param exam        the exam the test run belongs to
     * @param exerciseIds the ids of the exercises to include in the test run, in the exact order they should be persisted
     * @param workingTime the working time of the test run in seconds
     * @return the created testRun studentExam
     */
    public StudentExam createTestRun(Exam exam, List<Long> exerciseIds, Integer workingTime) {
        StudentExam testRun = transactionTemplate.execute(status -> {
            examRepository.findByIdWithPessimisticWriteLockElseThrow(exam.getId());
            List<Exercise> exercises = resolveExamExercises(exam, exerciseIds);
            return generateTestRun(exam, exercises, workingTime);
        });
        setUpTestRunExerciseParticipationsAndSubmissions(testRun.getId());
        return testRun;
    }

    /**
     * Loads the given exercises with a single query and returns them in the order of the given ids
     * (StudentExam.exercises is an @OrderColumn list, so the order must be preserved).
     * A test run may only contain exercises of the exam it belongs to; any other exercise id is rejected.
     *
     * @param exam        the exam the exercises must belong to
     * @param exerciseIds the ordered ids of the exercises to load
     * @return the exercises in the order of the given ids
     */
    private List<Exercise> resolveExamExercises(Exam exam, List<Long> exerciseIds) {
        Map<Long, Exercise> exercisesById = exerciseRepository.findAllById(exerciseIds).stream().collect(Collectors.toMap(Exercise::getId, Function.identity()));
        List<Exercise> exercises = new ArrayList<>(exerciseIds.size());
        for (Long exerciseId : exerciseIds) {
            Exercise exercise = exercisesById.get(exerciseId);
            if (exercise == null) {
                throw new EntityNotFoundException("Exercise", exerciseId);
            }
            Exam exerciseExam = exercise.getExam();
            if (exerciseExam == null || !exerciseExam.getId().equals(exam.getId())) {
                throw new ConflictException("The exercise does not belong to the exam", "Exercise", "exerciseExamConflict");
            }
            exercises.add(exercise);
        }
        return exercises;
    }

    /**
     * Create TestRun student exam based on the configuration provided.
     *
     * @param exam        the exam the test run belongs to
     * @param exercises   the exercises to include in the test run, in the exact order they should be persisted
     * @param workingTime the working time of the test run in seconds
     * @return The created test run
     */
    private StudentExam generateTestRun(Exam exam, List<Exercise> exercises, Integer workingTime) {
        StudentExam testRun = new StudentExam();
        testRun.setExercises(exercises);
        testRun.setExam(exam);
        testRun.setWorkingTime(workingTime);
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
        setUpExerciseParticipationsAndSubmissions(testRun, generatedParticipations, false);
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
        setUpExerciseParticipationsAndSubmissions(studentExam, generatedParticipations, false);
        // TODO: Michael Allgaier: schedule a lock operation for all involved student repositories of this student exam (test exam) at the end of the individual working time
        // Since students can participate in the test exam multiple times, we need to associate their exercise participations with a specific student exam
        if (!generatedParticipations.isEmpty()) {
            studentExam.setStudentParticipations(generatedParticipations);
            this.studentExamRepository.save(studentExam);
        }
        studentParticipationRepository.saveAll(generatedParticipations);
    }

    /**
     * Sets up the participations and submissions for all the exercises of the student exam.
     *
     * @param studentExam             The studentExam for which the participations and submissions should be created
     * @param generatedParticipations List of generated participations to track how many participations have been generated
     */
    private void setUpExerciseParticipationsAndSubmissions(StudentExam studentExam, List<StudentParticipation> generatedParticipations, boolean failFast) {
        User student = studentExam.getUser();

        for (Exercise exercise : studentExam.getExercises()) {
            // Stands in only if no caller context reached this thread; a real user's identity is kept.
            SecurityUtils.setAuthorizationObject();
            // NOTE: it's not ideal to invoke the next line several times (2000 student exams with 10 exercises would lead to 20.000 database calls to find all participations).
            // One optimization could be that we load all participations per exercise once (or per exercise) into a large list (10 * 2000 = 20.000 participations) and then check if
            // those participations exist in Java, however this might lead to memory issues and might be more difficult to program (and more difficult to understand)
            // TODO: directly check in the database if the entry exists for the student, exercise and InitializationState.INITIALIZED
            var studentParticipations = studentParticipationRepository.findByExerciseIdAndStudentId(exercise.getId(), student.getId());
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

                    if (!participation.isAtLeastInitialized()) {
                        throw new IllegalStateException("Participation " + participation.getId() + " was not initialized after starting exercise " + exercise.getId()
                                + " for student exam " + studentExam.getId() + " and student " + student.getParticipantIdentifier());
                    }

                    log.info("SUCCESS: Start exercise for student exam {} and exercise {} and student {}", studentExam.getId(), exercise.getId(),
                            student.getParticipantIdentifier());
                }
                catch (Exception ex) {
                    log.warn("FAILED: Start exercise for student exam {} and exercise {} and student {} with exception: {}", studentExam.getId(), exercise.getId(),
                            student.getParticipantIdentifier(), ex.getMessage(), ex);
                    if (failFast) {
                        throw ex;
                    }
                }
            }
        }
    }

    /**
     * Starts all the exercises only for a specific list of student exams.
     *
     * @param examId         exam to which the student exams belong
     * @param studentExamIds the ids of student exams for which to start exercises
     * @return a future that will yield the number of generated participations
     */
    public CompletableFuture<Integer> startExercisesForStudentExams(Long examId, List<Long> studentExamIds) {
        return this.startExercises(examId, studentExamIds);
    }

    /**
     * Starts all the exercises of all the student exams of an exam
     *
     * @param examId exam to which the student exams belong
     * @return a future that will yield the number of generated participations
     */
    public CompletableFuture<Integer> startExercises(Long examId) {
        return this.startExercises(examId, null);
    }

    private CompletableFuture<Integer> startExercises(Long examId, List<Long> studentExamIds) {
        var exam = examRepository.findWithStudentExamsExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        Set<StudentExam> studentExams;
        if (studentExamIds == null) {
            studentExams = exam.getStudentExams();
        }
        else {
            var studentExamsInExam = exam.getStudentExams().stream().collect(Collectors.toMap(DomainObject::getId, Function.identity()));
            studentExams = studentExamIds.stream().map(studentExamsInExam::get).filter(Objects::nonNull).collect(Collectors.toSet());
        }

        List<StudentParticipation> generatedParticipations = Collections.synchronizedList(new ArrayList<>());

        this.invalidateExerciseStartStatus(examId);

        var finishedExamsCounter = new AtomicInteger(0);
        var failedExamsCounter = new AtomicInteger(0);
        var startedAt = ZonedDateTime.now();
        var lock = new ReentrantLock();
        sendAndCacheExercisePreparationStatus(examId, 0, 0, studentExams.size(), 0, startedAt, lock);

        try (var threadPool = Executors.newFixedThreadPool(10)) {
            var futures = studentExams.stream()
                    .map(studentExam -> CompletableFuture.runAsync(() -> setUpExerciseParticipationsAndSubmissions(studentExam, generatedParticipations, true), threadPool)
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

    public void invalidateExerciseStartStatus(Long examId) {
        var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
        if (cache != null) {
            cache.evict(examId);
        }
    }

    /**
     * Generates a new individual StudentExam for the specified student and stores it in the database.
     * Locks the exam row and re-reads the exercise groups under that lock, so a concurrent exercise-group move
     * cannot desync the selection this generates. Called when a student starts a normal or test exam.
     *
     * @param exam    the exam to generate the student exam for
     * @param student The student for whom the StudentExam should be created.
     * @return The generated StudentExam.
     */
    public StudentExam generateIndividualStudentExam(Exam exam, User student) {
        long start = System.nanoTime();
        StudentExam studentExam = transactionTemplate.execute(status -> {
            examRepository.findByIdWithPessimisticWriteLockElseThrow(exam.getId());
            Exam lockedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
            return studentExamRepository.createRandomStudentExams(lockedExam, Set.of(student)).getFirst();
        });
        // we need to break a cycle for the serialization
        studentExam.getExam().setExerciseGroups(null);
        studentExam.getExam().setStudentExams(null);

        log.info("Generated 1 student exam for {} in {} for exam {}", student.getId(), formatDurationFrom(start), exam.getId());

        return studentExam;
    }

    /**
     * Generates the student exams randomly based on the exam configuration and the exercise groups.
     * Locks the exam row and re-reads the exercise groups under that lock, so a concurrent exercise-group move
     * cannot desync the selection this generates.
     *
     * @param exam the exam to generate student exams for
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateStudentExams(final Exam exam) {
        return transactionTemplate.execute(status -> {
            examRepository.findByIdWithPessimisticWriteLockElseThrow(exam.getId());
            Exam lockedExam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam.getId());

            this.invalidateExerciseStartStatus(lockedExam.getId());
            final var existingStudentExams = studentExamRepository.findByExamId(lockedExam.getId());
            // deleteInBatch does not work, because it does not cascade the deletion of existing exam sessions, therefore use deleteAll
            studentExamRepository.deleteAll(existingStudentExams);

            // StudentExams are saved in the called method
            return studentExamRepository.createRandomStudentExams(lockedExam, lockedExam.getRegisteredUsers());
        });
    }

    /**
     * Generates the missing student exams randomly based on the exam configuration and the exercise groups.
     * The difference between all registered users and the users who already have an individual exam is the set of users for which student exams will be created.
     * Locks the exam row and re-reads the exercise groups under that lock, so a concurrent exercise-group move
     * cannot desync the selection this generates.
     *
     * @param exam the exam to generate student exams for
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateMissingStudentExams(Exam exam) {
        return transactionTemplate.execute(status -> {
            examRepository.findByIdWithPessimisticWriteLockElseThrow(exam.getId());
            Exam lockedExam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam.getId());

            this.invalidateExerciseStartStatus(lockedExam.getId());

            // Get all users who already have an individual exam
            Set<User> usersWithStudentExam = studentExamRepository.findUsersWithStudentExamsForExam(lockedExam.getId());

            // Get all students who don't have an exam yet
            Set<User> missingUsers = lockedExam.getRegisteredUsers();
            missingUsers.removeAll(usersWithStudentExam);

            // StudentExams are saved in the called method
            return studentExamRepository.createRandomStudentExams(lockedExam, missingUsers);
        });
    }
}
