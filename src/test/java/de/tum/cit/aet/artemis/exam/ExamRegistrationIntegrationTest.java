package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.CreateTestRunDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamRegistrationResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamPreparationLockService;
import de.tum.cit.aet.artemis.exam.service.ExamRegistrationService;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationCleanupCommand;
import de.tum.cit.aet.artemis.exercise.service.ParticipationCleanupCommand.Operation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationCleanupService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.FailedBuildLogService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExamRegistrationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "examregistrationtest";

    /** Student not enrolled in the test course; exercises the not-in-course branches. */
    private static final String OTHER_PREFIX = "examregistrationother";

    public static final String STUDENT_111 = TEST_PREFIX + "student111";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ExamRegistrationService examRegistrationService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ExamPreparationLockService examPreparationLockService;

    @Autowired
    private DistributedDataProvider distributedDataProvider;

    @Autowired
    private ParticipationCleanupService participationCleanupService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private FailedBuildLogService failedBuildLogService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    private Course course1;

    private Exam exam1;

    private Exam testExam1;

    private static final int NUMBER_OF_STUDENTS = 3;

    private static final int NUMBER_OF_TUTORS = 1;

    private static final int NUMBER_OF_EDITORS = 1;

    private static final int NUMBER_OF_INSTRUCTORS = 1;

    private User student1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, NUMBER_OF_EDITORS, NUMBER_OF_INSTRUCTORS);
        userUtilService.addUsers(OTHER_PREFIX, 1, 0, 0, 0); // outsider student — never enrolled in course

        course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        exam1 = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam1, "exam1 channel");
        testExam1 = examUtilService.addTestExam(course1);
        examUtilService.addStudentExamForTestExam(testExam1, student1);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
    }

    @AfterEach
    void tearDown() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
        participantScoreScheduleService.shutdown();
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing", "individual", "regenerate" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGenerationPathsRejectConcurrentGenerationAndAllowRetryWithoutDuplicates(String generationPath) throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        exam.setExamMaxPoints(exam.getExerciseGroups().stream().mapToInt(group -> group.getExercises().iterator().next().getMaxPoints().intValue()).sum());
        examRepository.save(exam);
        long examId = exam.getId();
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var securityContext = SecurityContextHolder.getContext();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> examRepository.withStudentExamGenerationLock(examId, () -> {
                firstEntered.countDown();
                try {
                    assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return studentExamService.generateMissingStudentExams(examRepository.findByIdElseThrow(examId));
            }));
            try {
                assertThat(firstEntered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    SecurityContextHolder.setContext(securityContext);
                    secondStarted.countDown();
                    try {
                        Exam current = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
                        switch (generationPath) {
                            case "missing" ->
                                assertThatThrownBy(() -> studentExamService.generateMissingStudentExams(current)).isInstanceOf(PessimisticLockingFailureException.class);
                            case "individual" -> assertThatThrownBy(() -> studentExamService.generateIndividualStudentExam(current, student1))
                                    .isInstanceOf(PessimisticLockingFailureException.class);
                            case "regenerate" -> request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/generate-student-exams",
                                    Optional.empty(), StudentExamDTO.class, HttpStatus.CONFLICT);
                            default -> throw new IllegalArgumentException(generationPath);
                        }
                        return null;
                    }
                    finally {
                        SecurityContextHolder.clearContext();
                    }
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                second.get(2, TimeUnit.SECONDS);
                assertThat(first.isDone()).isFalse();
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                Exam current = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
                switch (generationPath) {
                    case "missing" -> studentExamService.generateMissingStudentExams(current);
                    case "individual" -> studentExamService.generateIndividualStudentExam(current, student1);
                    case "regenerate" -> request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/generate-student-exams", Optional.empty(),
                            StudentExamDTO.class, HttpStatus.OK);
                    default -> throw new IllegalArgumentException(generationPath);
                }
                assertThat(studentExamRepository.findByExamId(examId)).singleElement()
                        .satisfies(studentExam -> assertThat(studentExam.getUser().getId()).isEqualTo(student1.getId()));
                assertThat(studentExamRepository.findWithExercisesByUserIdAndExamId(student1.getId(), examId, false)).isPresent();
            }
            finally {
                releaseFirst.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegenerationCleansExternalRepositoryOnlyAfterSuccessfulCommit(boolean failGeneration) throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        exam.setExamMaxPoints(exam.getExerciseGroups().stream().mapToInt(group -> group.getExercises().iterator().next().getMaxPoints().intValue()).sum());
        examRepository.save(exam);
        long examId = exam.getId();
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        var previousExam = studentExamService.generateMissingStudentExams(exam).getFirst();
        var exercise = (ProgrammingExercise) previousExam.getExercises().stream().filter(ProgrammingExercise.class::isInstance).findFirst().orElseThrow();
        var participation = localVCLocalCITestService.createParticipation(exercise, student1.getLogin());
        var repositoryUri = (LocalVCRepositoryUri) participation.getVcsRepositoryUri();
        localVCLocalCITestService.createRepository(repositoryUri.getProjectKey(), localVCLocalCITestService.getRepositorySlug(repositoryUri.getProjectKey(), student1.getLogin()));
        Path repositoryPath = repositoryUri.getLocalRepositoryPath(localVCBasePath);
        assertThat(repositoryPath).exists();
        var oldTestRun = studentExamService.createTestRun(exam, List.of(exercise.getId()), 3600);
        var oldTestRunParticipation = programmingExerciseStudentParticipationRepository
                .findAllWithSubmissionsByExerciseIdAndStudentId(exercise.getId(), oldTestRun.getUser().getId()).stream()
                .filter(participationToCheck -> participationToCheck.isTestRun()).findFirst().orElseThrow();
        Path testRunRepositoryPath = ((LocalVCRepositoryUri) oldTestRunParticipation.getVcsRepositoryUri()).getLocalRepositoryPath(localVCBasePath);
        assertThat(testRunRepositoryPath).exists();
        var testRunConfiguration = new CreateTestRunDTO(examId, List.of(exercise.getId()), 3600);
        String testRunUrl = "/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/test-runs";
        var result = participationUtilService.addSampleFeedbackToResults(participationUtilService.createSubmissionAndResult(participation, 0, true));
        var storedResult = resultRepository.findWithSubmissionAndFeedbackAndTeamStudentsByIdElseThrow(result.getId());
        assertThat(storedResult.getCompletionDate()).isNotNull();
        assertThat(storedResult.getFeedbacks()).hasSize(2);
        var submission = (ProgrammingSubmission) result.getSubmission();
        var buildLogs = List.of(new BuildLogEntry(ZonedDateTime.parse("2026-01-15T10:00:00Z"), "Compiler failure retained across rollback"));
        buildLogEntryService.saveBuildLogs(buildLogs, submission, result);
        assertThat(failedBuildLogService.getBuildLogs(exercise.getId(), submission.getId(), result.getId())).isPresent();
        var generationCompleted = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(repositoryPath).as("external resources must survive until the generation transaction commits").exists();
            assertThat(failedBuildLogService.getBuildLogs(exercise.getId(), submission.getId(), result.getId())).as("build-log files must also survive until commit").isPresent();
            var generated = invocation.callRealMethod();
            generationCompleted.set(true);
            if (failGeneration) {
                throw new IllegalStateException("Force replacement generation rollback");
            }
            return generated;
        }).when(studentExamService).generateStudentExams(any(Exam.class));

        String generationUrl = "/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/generate-student-exams";
        if (failGeneration) {
            request.postListWithResponseBody(generationUrl, Optional.empty(), StudentExamDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        else {
            var cleanupEntered = new CountDownLatch(1);
            var releaseCleanup = new CountDownLatch(1);
            doAnswer(invocation -> {
                // This query runs after the generation repository transaction returned, proving its deletion committed.
                assertThat(programmingExerciseStudentParticipationRepository.findById(participation.getId())).isEmpty();
                cleanupEntered.countDown();
                assertThat(releaseCleanup.await(10, TimeUnit.SECONDS)).isTrue();
                return invocation.callRealMethod();
            }).when(gitServiceSpy).deleteLocalRepository(repositoryUri);
            var context = SecurityContextHolder.getContext();
            try (var executor = Executors.newSingleThreadExecutor()) {
                var regeneration = executor.submit(() -> {
                    SecurityContextHolder.setContext(context);
                    try {
                        return request.postListWithResponseBody(generationUrl, Optional.empty(), StudentExamDTO.class, HttpStatus.OK);
                    }
                    finally {
                        SecurityContextHolder.clearContext();
                    }
                });
                try {
                    assertThat(cleanupEntered.await(10, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> studentExamService.startExercises(examId)).isInstanceOf(CannotAcquireLockException.class);
                    request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/student-exams/start-exercises", Optional.empty(),
                            HttpStatus.CONFLICT);
                    request.postWithResponseBody(testRunUrl, testRunConfiguration, StudentExamDTO.class, HttpStatus.CONFLICT);
                    assertThat(studentExamRepository.findAllTestRunsByExamId(examId)).isEmpty();
                    userUtilService.changeUser(student1.getLogin());
                    try {
                        request.postWithoutResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", Optional.empty(), HttpStatus.CONFLICT);
                        assertThat(studentParticipationRepository.findByExerciseId(exercise.getId())).isEmpty();
                        request.putWithResponseBody(
                                "/api/exercise/exercises/" + exercise.getId() + "/participations/" + participation.getId() + "/resume-programming-participation", null,
                                StudentParticipationDTO.class, HttpStatus.CONFLICT);
                    }
                    finally {
                        userUtilService.changeUser(TEST_PREFIX + "instructor1");
                    }
                    assertThat(regeneration.isDone()).isFalse();
                }
                finally {
                    releaseCleanup.countDown();
                }
                regeneration.get(10, TimeUnit.SECONDS);
            }
        }
        verify(studentExamService).generateStudentExams(any(Exam.class));
        assertThat(generationCompleted.get()).as("the injected failure must occur after replacement generation, not during deletion or fixture assertions").isTrue();
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThat(executor.submit(() -> examPreparationLockService.withPreparationLock(examId, () -> true)).get(2, TimeUnit.SECONDS))
                    .as("success and rollback both release preparation admission for another thread").isTrue();
        }

        if (failGeneration) {
            assertThat(repositoryPath).exists();
            assertThat(failedBuildLogService.getBuildLogs(exercise.getId(), submission.getId(), result.getId()).orElseThrow()).singleElement().extracting(BuildLogEntry::getLog)
                    .isEqualTo("Compiler failure retained across rollback");
            assertThat(programmingExerciseStudentParticipationRepository.findById(participation.getId())).isPresent();
            assertThat(resultRepository.findWithSubmissionAndFeedbackAndTeamStudentsByIdElseThrow(result.getId()).getFeedbacks()).hasSize(2);
            assertThat(studentExamRepository.findByExamId(examId).stream().filter(studentExam -> !studentExam.isTestRun()).toList()).singleElement().extracting(StudentExam::getId)
                    .isEqualTo(previousExam.getId());
            assertThat(studentExamRepository.findAllTestRunsByExamId(examId)).singleElement().extracting(StudentExam::getId).isEqualTo(oldTestRun.getId());
            assertThat(testRunRepositoryPath).exists();
        }
        else {
            assertThat(repositoryPath).doesNotExist();
            assertThat(failedBuildLogService.getBuildLogs(exercise.getId(), submission.getId(), result.getId())).isEmpty();
            assertThat(programmingExerciseStudentParticipationRepository.findById(participation.getId())).isEmpty();
            assertThat(resultRepository.findById(result.getId())).isEmpty();
            assertThat(studentExamRepository.findByExamId(examId)).singleElement().extracting(StudentExam::getId).isNotEqualTo(previousExam.getId());
            assertThat(testRunRepositoryPath).doesNotExist();
            userUtilService.changeUser(student1.getLogin());
            try {
                var replacement = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", Optional.empty(), StudentParticipationDTO.class,
                        HttpStatus.CREATED);
                assertThat(repositoryPath).exists();
                request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations/" + participation.getId() + "/resume-programming-participation", null,
                        StudentParticipationDTO.class, HttpStatus.NOT_FOUND);
                assertThat(repositoryPath).as("a stale resume cannot touch the replacement repository").exists();

                // Once cleanup releases admission, resume the replacement's inactive build plan without replacing its repository or identity.
                var inactive = programmingExerciseStudentParticipationRepository.findByIdElseThrow(replacement.id());
                inactive.setInitializationState(InitializationState.INACTIVE);
                inactive.setBuildPlanId(null);
                programmingExerciseStudentParticipationRepository.saveAndFlush(inactive);
                var resumed = request.putWithResponseBody(
                        "/api/exercise/exercises/" + exercise.getId() + "/participations/" + replacement.id() + "/resume-programming-participation", null,
                        StudentParticipationDTO.class, HttpStatus.OK);
                assertThat(resumed.id()).isEqualTo(replacement.id());
                assertThat(resumed.repositoryUri()).isEqualTo(replacement.repositoryUri());
                assertThat(resumed.initializationState()).isEqualTo(InitializationState.INITIALIZED);
                assertThat(resumed.buildPlanId()).isNotBlank();
                assertThat(repositoryPath).exists();
                assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(exercise.getId())).singleElement()
                        .extracting(existingParticipation -> existingParticipation.getId()).isEqualTo(replacement.id());
            }
            finally {
                userUtilService.changeUser(TEST_PREFIX + "instructor1");
            }
            request.postWithResponseBody(testRunUrl, testRunConfiguration, StudentExamDTO.class, HttpStatus.OK);
            assertThat(studentExamRepository.findAllTestRunsByExamId(examId)).hasSize(1);
            assertThat(testRunRepositoryPath).as("retry recreates a repository only after old cleanup finished").exists();
        }
        assertThat(examUserRepository.findAllByExamId(examId)).singleElement().extracting(examUser -> examUser.getUser().getId()).isEqualTo(student1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRetainedCompetencyCleanupAfterExerciseDeletionAdmitsPreparation() throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        long examId = exam.getId();
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        var studentExam = studentExamService.generateStudentExams(exam).getFirst();
        var exercise = studentExam.getExercises().stream().filter(TextExercise.class::isInstance).findFirst().orElseThrow();
        long exerciseId = exercise.getId();
        var competency = competencyUtilService.createCompetencyWithExercise(course1, exercise);
        var command = new ParticipationCleanupCommand(Operation.COMPETENCY_PROGRESS, 0, exerciseId, 0, null, null, null);

        participationCleanupService.execute(command);
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(org.mockito.ArgumentMatchers.argThat(learningObject -> learningObject.getId().equals(exerciseId)));

        // Cleanup intent may survive until an instructor deletes an exercise from the still-existing exam.
        var cleanupMap = distributedDataProvider.<Long, List<ParticipationCleanupCommand>>getMap("exam-unfinished-resource-cleanup");
        cleanupMap.put(examId, List.of(command));
        try {
            request.delete("/api/text/text-exercises/" + exerciseId, HttpStatus.OK);
            assertThat(exerciseRepository.findById(exerciseId)).isEmpty();
            assertThat(examRepository.findById(examId)).isPresent();
            assertThat(studentExamRepository.findByIdWithExercisesElseThrow(studentExam.getId()).getExercises()).extracting(assignedExercise -> assignedExercise.getId())
                    .doesNotContain(exerciseId);
            verify(competencyProgressApi)
                    .updateProgressByCompetencyAsync(org.mockito.ArgumentMatchers.argThat(linkedCompetency -> linkedCompetency.getId().equals(competency.getId())));

            assertThat(examPreparationLockService.withPreparationLock(examId, () -> "admitted")).isEqualTo("admitted");
            assertThat(cleanupMap.get(examId)).isNull();
        }
        finally {
            cleanupMap.remove(examId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "ci", "vc", "ci-local" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegenerationRetainsFailedCleanupAndReplaysItBeforePreparingNewRepositories(String failedAdapter) throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        exam.setExamMaxPoints(exam.getExerciseGroups().stream().mapToInt(group -> group.getExercises().iterator().next().getMaxPoints().intValue()).sum());
        examRepository.save(exam);
        var otherStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var registered = new ExamUser();
        registered.setExam(exam);
        registered.setUser(otherStudent);
        examUserRepository.save(registered);
        var previousIds = studentExamService.generateStudentExams(exam).stream().map(StudentExam::getId).toList();
        studentExamService.startExercises(exam.getId()).join();
        var exercise = exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(ProgrammingExercise.class::isInstance)
                .map(ProgrammingExercise.class::cast).findFirst().orElseThrow();
        var participations = new ArrayList<>(programmingExerciseStudentParticipationRepository.findByExerciseId(exercise.getId()));
        assertThat(participations).hasSize(2);
        var repositoryPaths = new ArrayList<Path>();
        var submissions = new ArrayList<ProgrammingSubmission>();
        var resultIds = new ArrayList<Long>();
        for (var participation : participations) {
            Path repositoryPath = ((LocalVCRepositoryUri) participation.getVcsRepositoryUri()).getLocalRepositoryPath(localVCBasePath);
            assertThat(repositoryPath).exists();
            repositoryPaths.add(repositoryPath);
            var uri = (LocalVCRepositoryUri) participation.getVcsRepositoryUri();
            String slug = localVCLocalCITestService.getRepositorySlug(uri.getProjectKey(), uri.getRepositoryTypeOrUserName());
            try (var repository = localVCLocalCITestService.cloneWorkingCopy(uri.getProjectKey(), slug)) {
                FileUtils.writeStringToFile(repository.workingCopyPath().resolve("old-student-answer.txt").toFile(), "Previous private student answer", StandardCharsets.UTF_8);
                repository.workingCopy().add().addFilepattern("old-student-answer.txt").call();
                de.tum.cit.aet.artemis.localvc.service.GitService.commit(repository.workingCopy()).setMessage("Commit previous student answer").call();
                repository.workingCopy().push().call();
            }
            var result = participationUtilService.createSubmissionAndResult(participation, 80, true);
            var submission = (ProgrammingSubmission) result.getSubmission();
            buildLogEntryService.saveBuildLogs(List.of(new BuildLogEntry(ZonedDateTime.parse("2026-01-15T10:00:00Z"), "Retained until commit")), submission, result);
            assertThat(failedBuildLogService.getBuildLogs(exercise.getId(), submission.getId(), result.getId())).isPresent();
            submissions.add(submission);
            resultIds.add(result.getId());
        }
        var sharedFailure = new IllegalStateException("Injected cleanup failure");
        var localFailure = new IllegalStateException("Injected local cleanup failure");
        if (!failedAdapter.equals("vc")) {
            org.mockito.Mockito.doThrow(sharedFailure).when(continuousIntegrationService).deleteBuildPlan(org.mockito.ArgumentMatchers.eq(exercise.getProjectKey()),
                    org.mockito.ArgumentMatchers.anyString());
        }
        else {
            org.mockito.Mockito.doThrow(sharedFailure).when(versionControlService).deleteRepository(org.mockito.ArgumentMatchers.any(LocalVCRepositoryUri.class));
        }
        if (failedAdapter.equals("ci-local")) {
            org.mockito.Mockito.doThrow(localFailure).when(gitServiceSpy).deleteLocalRepository(org.mockito.ArgumentMatchers.any(LocalVCRepositoryUri.class));
        }

        try {
            var committed = request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", null,
                    StudentExamDTO.class, HttpStatus.OK, null, Map.of("X-Artemis-Preparation-Pending", "true"));
            var committedIds = committed.stream().map(StudentExamDTO::id).toList();
            assertThat(committedIds).hasSize(2).doesNotContainAnyElementsOf(previousIds);
            assertThat(studentExamRepository.findByExamId(exam.getId())).extracting(StudentExam::getId).containsExactlyInAnyOrderElementsOf(committedIds);
            for (int index = 0; index < participations.size(); index++) {
                assertThat(programmingExerciseStudentParticipationRepository.findById(participations.get(index).getId())).isEmpty();
                if (failedAdapter.equals("vc")) {
                    assertThat(repositoryPaths.get(index)).exists();
                    var uri = (LocalVCRepositoryUri) participations.get(index).getVcsRepositoryUri();
                    String slug = localVCLocalCITestService.getRepositorySlug(uri.getProjectKey(), uri.getRepositoryTypeOrUserName());
                    try (var repository = localVCLocalCITestService.cloneWorkingCopy(uri.getProjectKey(), slug)) {
                        assertThat(Files.readString(repository.workingCopyPath().resolve("old-student-answer.txt"))).isEqualTo("Previous private student answer");
                    }
                }
                else {
                    assertThat(repositoryPaths.get(index)).doesNotExist();
                }
                assertThat(failedBuildLogService.getBuildLogs(exercise.getId(), submissions.get(index).getId(), resultIds.get(index))).isEmpty();
                verify(gitServiceSpy).deleteLocalRepository((LocalVCRepositoryUri) participations.get(index).getVcsRepositoryUri());
            }
            verify(continuousIntegrationService).deleteBuildPlan(exercise.getProjectKey(), participations.get(1).getBuildPlanId());
            long examId = exam.getId();
            var cleanupMap = distributedDataProvider.<Long, List<ParticipationCleanupCommand>>getMap("exam-unfinished-resource-cleanup");
            if (failedAdapter.equals("ci-local")) {
                var retained = cleanupMap.get(examId);
                assertThat(retained).hasSize(4);
                for (var participation : participations) {
                    assertThat(retained).filteredOn(command -> command.participationId() == participation.getId()).extracting(ParticipationCleanupCommand::operation)
                            .containsExactlyInAnyOrder(Operation.BUILD_PLAN, Operation.LOCAL_REPOSITORY);
                }
            }
            assertThatThrownBy(() -> studentExamService.startExercises(examId)).isInstanceOf(CannotAcquireLockException.class).satisfies(exception -> {
                if (failedAdapter.equals("ci-local")) {
                    assertThat(exception.getCause().getSuppressed()).containsExactlyInAnyOrder(sharedFailure, sharedFailure, localFailure, localFailure);
                }
            });
            assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(exercise.getId())).isEmpty();
            org.mockito.Mockito.doCallRealMethod().when(continuousIntegrationService).deleteBuildPlan(org.mockito.ArgumentMatchers.eq(exercise.getProjectKey()),
                    org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.doCallRealMethod().when(versionControlService).deleteRepository(org.mockito.ArgumentMatchers.any(LocalVCRepositoryUri.class));
            org.mockito.Mockito.doCallRealMethod().when(gitServiceSpy).deleteLocalRepository(org.mockito.ArgumentMatchers.any(LocalVCRepositoryUri.class));
            studentExamService.startExercises(examId).join();
            assertThat(cleanupMap.get(examId)).isNull();
            assertThat(studentExamRepository.findByExamId(exam.getId())).extracting(StudentExam::getId).containsExactlyInAnyOrderElementsOf(committedIds);
            assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(exercise.getId())).hasSize(2);
            for (var oldParticipation : participations) {
                var uri = (LocalVCRepositoryUri) oldParticipation.getVcsRepositoryUri();
                String slug = localVCLocalCITestService.getRepositorySlug(uri.getProjectKey(), uri.getRepositoryTypeOrUserName());
                try (var repository = localVCLocalCITestService.cloneWorkingCopy(uri.getProjectKey(), slug)) {
                    assertThat(repository.workingCopyPath().resolve("old-student-answer.txt")).doesNotExist();
                }
            }
        }
        finally {
            org.mockito.Mockito.doCallRealMethod().when(continuousIntegrationService).deleteBuildPlan(org.mockito.ArgumentMatchers.eq(exercise.getProjectKey()),
                    org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.doCallRealMethod().when(versionControlService).deleteRepository(org.mockito.ArgumentMatchers.any(LocalVCRepositoryUri.class));
            org.mockito.Mockito.doCallRealMethod().when(gitServiceSpy).deleteLocalRepository(org.mockito.ArgumentMatchers.any(LocalVCRepositoryUri.class));
            examPreparationLockService.withPreparationLock(exam.getId(), () -> null);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testIndividualGenerationReusesRealExamAndPreservesMultipleTestExamAttempts() {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        StudentExam first = studentExamService.generateIndividualStudentExam(exam, student1);
        assertThat(studentExamService.generateIndividualStudentExam(exam, student1).getId()).isEqualTo(first.getId());
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(1);

        exam.setTestExam(true);
        examRepository.save(exam);
        StudentExam attempt = studentExamService.generateIndividualStudentExam(exam, student1);
        assertThat(attempt.getId()).isNotEqualTo(first.getId());
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationRejectedAfterExamEnd() throws Exception {
        exam1.setStartDate(ZonedDateTime.now().minusHours(2));
        exam1.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam1);
        var student = new StudentDTO(OTHER_PREFIX + "student1", "", "", "", "");
        String base = "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId();
        request.postWithoutLocation(base + "/students", List.of(student), HttpStatus.BAD_REQUEST, null);
        request.postWithoutLocation(base + "/register-course-students", null, HttpStatus.BAD_REQUEST, null);
        assertThat(examUserRepository.findAllByExamId(exam1.getId())).isEmpty();
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(userUtilService.getUserByLogin(OTHER_PREFIX + "student1").getId(), course1.getId(),
                CourseRole.STUDENT)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationGeneratesMissingExamsBeforeStart() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        examUtilService.addStudentExamWithUser(exam, student1);
        var student = new StudentDTO(OTHER_PREFIX + "student1", "", "", "", "");
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", List.of(student), HttpStatus.OK, null);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testBulkRegistrationGeneratesMissingExamsBeforeStart() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        examUtilService.addStudentExamWithUser(exam, student1);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/register-course-students", null, HttpStatus.OK, null);
        assertThat(studentExamRepository.findUserIdsWithStudentExamsForExam(exam.getId()))
                .containsExactlyInAnyOrderElementsOf(examUserRepository.findUserIdsByExamId(exam.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationPreparesExercisesBeforeStartAfterProgressCacheExpires() throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        studentExamService.generateStudentExams(exam);
        studentExamService.startExercises(exam.getId()).join();
        studentExamService.invalidateExerciseStartStatus(exam.getId());
        var student = new StudentDTO(OTHER_PREFIX + "student1", "", "", "", "");
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", List.of(student), HttpStatus.OK, null);
        long examId = exam.getId();
        var exerciseIds = exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).map(exercise -> exercise.getId()).toList();
        long expectedParticipations = 2L * exam.getNumberOfExercisesInExam();
        await().untilAsserted(() -> assertThat(exerciseIds.stream()
                .mapToLong(exerciseId -> studentParticipationRepository.countInitializedParticipationsByExerciseIdAndExamIdIgnoreTestRuns(exerciseId, examId)).sum())
                .isEqualTo(expectedParticipations));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationReportsFailedPreparationAndRetriesExistingStudentExam(boolean allCourseStudents) throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        long examId = exam.getId();
        // The helper's empty placeholder is replaced by generation in the real endpoint.
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        studentExamService.generateStudentExams(exam);
        studentExamService.startExercises(examId).join();
        var preparedStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student1.getId(), examId, false).orElseThrow();
        var initializedParticipationIds = studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(preparedStudentExam, false).stream()
                .map(participation -> participation.getId()).toList();
        User newStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var payload = allCourseStudents ? null : List.of(new StudentDTO(newStudent.getLogin(), "", "", "", ""));
        String url = "/api/exam/courses/" + course1.getId() + "/exams/" + examId + (allCourseStudents ? "/register-course-students" : "/students");
        org.mockito.Mockito.doThrow(new IllegalStateException("Injected participation preparation failure")).when(continuousIntegrationService).configureBuildPlan(any());
        Long savedStudentExamId;
        try {
            var response = request.postWithoutResponseBody(url, payload, HttpStatus.OK, null);
            assertThat(studentExamService.getExerciseStartStatusOfExam(examId).orElseThrow().failed()).isPositive();
            assertThat(examUserRepository.findUserIdsByExamId(examId)).contains(newStudent.getId());
            savedStudentExamId = studentExamRepository.findWithExercisesByUserIdAndExamId(newStudent.getId(), examId, false).orElseThrow().getId();
            assertThat(response.getHeader("X-Artemis-Preparation-Pending")).isEqualTo("true");
        }
        finally {
            org.mockito.Mockito.doCallRealMethod().when(continuousIntegrationService).configureBuildPlan(any());
        }
        var response = request.postWithoutResponseBody(url, payload, HttpStatus.OK, null);
        assertThat(response.getHeader("X-Artemis-Preparation-Pending")).isNull();
        var retriedStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(newStudent.getId(), examId, false).orElseThrow();
        assertThat(retriedStudentExam.getId()).isEqualTo(savedStudentExamId);
        assertThat(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(retriedStudentExam, false)).hasSize(exam.getNumberOfExercisesInExam())
                .allMatch(participation -> participation.isAtLeastInitialized());
        assertThat(studentExamService.getExerciseStartStatusOfExam(examId).orElseThrow().failed()).isZero();
        assertThat(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(preparedStudentExam, false)).extracting(participation -> participation.getId())
                .containsExactlyInAnyOrderElementsOf(initializedParticipationIds);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationRetriesAllFailedPreparationWithoutInitializedParticipations(boolean cacheExpired) throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        ExamFactory.generateExerciseGroup(true, exam);
        exam.setNumberOfExercisesInExam(1);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        exam = examRepository.save(exam);
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToExam(exam, 0);
        long examId = exam.getId();
        // The helper's empty placeholder is replaced by generation in the real endpoint.
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        studentExamService.generateStudentExams(exam);
        var newStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var payload = List.of(new StudentDTO(newStudent.getLogin(), "", "", "", ""));
        String url = "/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/students";
        org.mockito.Mockito.doThrow(new IllegalStateException("Injected first preparation failure")).when(continuousIntegrationService).configureBuildPlan(any());
        List<Long> originalParticipationIds;
        try {
            studentExamService.startExercises(examId).join();
            var failedStatus = studentExamService.getExerciseStartStatusOfExam(examId).orElseThrow();
            assertThat(failedStatus.failed()).isEqualTo(1);
            assertThat(failedStatus.finished()).isZero();
            assertThat(studentParticipationRepository.countInitializedParticipationsByExerciseIdAndExamIdIgnoreTestRuns(exercise.getId(), examId)).isZero();
            originalParticipationIds = programmingExerciseStudentParticipationRepository.findByExerciseId(exercise.getId()).stream().map(participation -> participation.getId())
                    .toList();
            assertThat(originalParticipationIds).hasSize(1);
            if (cacheExpired) {
                studentExamService.invalidateExerciseStartStatus(examId);
            }
            var response = request.postWithoutResponseBody(url, payload, HttpStatus.OK, null);
            assertThat(response.getHeader("X-Artemis-Preparation-Pending")).isEqualTo("true");
        }
        finally {
            org.mockito.Mockito.doCallRealMethod().when(continuousIntegrationService).configureBuildPlan(any());
        }
        var response = request.postWithoutResponseBody(url, payload, HttpStatus.OK, null);
        assertThat(response.getHeader("X-Artemis-Preparation-Pending")).isNull();
        assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(exercise.getId())).hasSize(2).allMatch(participation -> participation.isAtLeastInitialized())
                .extracting(participation -> participation.getId()).containsAll(originalParticipationIds);
        assertThat(studentExamService.getExerciseStartStatusOfExam(examId).orElseThrow().failed()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationDefersStudentExamCreationWhenPreparationIsBusyAndRetryPreparesIt(boolean allCourseStudents) throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        long examId = exam.getId();
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        studentExamService.generateMissingStudentExams(exam);
        assertThat(studentExamService.getExerciseStartStatusOfExam(examId)).isEmpty();
        User newStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student = new StudentDTO(newStudent.getLogin(), "", "", "", "");
        String url = "/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/students";
        if (allCourseStudents) {
            url = "/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/register-course-students";
        }
        int auditCount = auditEventRepository.find(TEST_PREFIX + "instructor1", Instant.EPOCH, Constants.ADD_USER_TO_EXAM).size();
        String examTitle = exam.getTitle();
        var preparationEntered = new CountDownLatch(1);
        var releasePreparation = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var preparation = executor.submit(() -> examPreparationLockService.withPreparationLock(examId, () -> {
                preparationEntered.countDown();
                try {
                    assertThat(releasePreparation.await(10, TimeUnit.SECONDS)).isTrue();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            try {
                assertThat(preparationEntered.await(10, TimeUnit.SECONDS)).isTrue();
                if (allCourseStudents) {
                    var response = request.postWithoutResponseBody(url, null, HttpStatus.OK, null, Map.of("X-Artemis-Preparation-Pending", "true"));
                    assertThat(response.getContentAsString()).isEmpty();
                }
                else {
                    var response = request.postWithResponseBody(url, List.of(student), ExamRegistrationResultDTO.class, HttpStatus.OK, null,
                            Map.of("X-Artemis-Preparation-Pending", "true"));
                    assertThat(response.notFoundStudents()).isNullOrEmpty();
                    assertThat(response.rejectedStaffUsers()).isNullOrEmpty();
                }
                assertThat(examUserRepository.findUserIdsByExamId(examId)).contains(newStudent.getId());
                assertThat(studentExamRepository.findWithExercisesByUserIdAndExamId(newStudent.getId(), examId, false)).isEmpty();
                assertThat(auditEventRepository.find(TEST_PREFIX + "instructor1", Instant.EPOCH, Constants.ADD_USER_TO_EXAM)).hasSize(auditCount + 1)
                        .anySatisfy(event -> assertThat(event.getData()).containsEntry("exam", examTitle));
            }
            finally {
                releasePreparation.countDown();
            }
            preparation.get(10, TimeUnit.SECONDS);
        }

        // The first preparation completes before retry and its progress cache expires. Admission must re-read persisted preparation state.
        studentExamService.startExercises(examId).join();
        studentExamService.invalidateExerciseStartStatus(examId);
        var retried = request.postWithoutResponseBody(url, allCourseStudents ? null : List.of(student), HttpStatus.OK, null);
        assertThat(retried.getHeader("X-Artemis-Preparation-Pending")).isNull();

        var generated = studentExamRepository.findWithExercisesByUserIdAndExamId(newStudent.getId(), examId, false).orElseThrow();
        assertThat(generated.getExercises()).hasSize(exam.getNumberOfExercisesInExam());
        assertThat(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(generated, false)).hasSize(exam.getNumberOfExercisesInExam());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegistrationRechecksPreparationThatCompletesWhileResolvingStudents() throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);
        long examId = exam.getId();
        studentExamRepository.deleteAll(studentExamRepository.findByExamId(examId));
        studentExamService.generateMissingStudentExams(exam);
        assertThat(studentExamService.getExerciseStartStatusOfExam(examId)).isEmpty();
        String login = TEST_PREFIX + "fastprepstudent";
        var ldapStudent = new LdapUserDto().firstName("Fast").lastName("Preparation").login(login).registrationNumber("79870122").email(login + "@tum.de");
        doAnswer(invocation -> {
            // Registration has begun, but user resolution can take long enough for a first preparation to finish.
            studentExamService.startExercises(examId).join();
            studentExamService.invalidateExerciseStartStatus(examId);
            return Optional.of(ldapStudent);
        }).when(ldapUserService).findByLogin(login);

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + examId + "/students", List.of(new StudentDTO(login, "", "", "", "")), HttpStatus.OK, null);

        var generated = studentExamRepository.findWithExercisesByUserIdAndExamId(userUtilService.getUserByLogin(login).getId(), examId, false).orElseThrow();
        assertThat(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(generated, false)).hasSize(exam.getNumberOfExercisesInExam());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterUserInExam_addedToCourseStudentsGroup() throws Exception {
        int studentCountBefore = userCourseRoleTestRepository.findByCourse_IdAndRole(course1.getId(), CourseRole.STUDENT).size();
        var student = new StudentDTO(OTHER_PREFIX + "student1", "", "", "", "");
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", List.of(student), HttpStatus.OK, null);
        assertThat(userCourseRoleTestRepository.findByCourse_IdAndRole(course1.getId(), CourseRole.STUDENT)).as("student was enrolled in course as STUDENT via UCR")
                .hasSize(studentCountBefore + 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterUsersInExam_duplicateRowsForSameUserCreateSingleExamUser() throws Exception {
        // exam_user has no unique constraint on (exam_id, student_id), so a request (or an imported CSV) listing the same
        // user twice must be deduplicated in the service: two rows would both be persisted and then break
        // findByExamIdAndUserId, which returns an Optional.
        Exam exam = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam, "duplicate-rows-channel");
        String login = OTHER_PREFIX + "student1";
        User student = userUtilService.getUserByLogin(login);

        var firstRow = new ExamUserDTO(login, "", "", "", "", "", "101", "11", false, false, false, false, "", null, null, null, null, null, null, null);
        // Second row repeats the user with a new room but a blank seat: the last non-blank value wins per field.
        var secondRow = new ExamUserDTO(login, "", "", "", "", "", "202", "", false, false, false, false, "", null, null, null, null, null, null, null);

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", List.of(firstRow, secondRow), HttpStatus.OK, null);

        Exam storedExam = examRepository.findWithExamUsersById(exam.getId()).orElseThrow();
        assertThat(storedExam.getExamUsers()).as("the duplicated user is registered exactly once").hasSize(1);
        // Would throw IncorrectResultSizeDataAccessException if duplicates had been persisted.
        ExamUser examUser = examUserRepository.findByExamIdAndUserId(exam.getId(), student.getId()).orElseThrow();
        assertThat(examUser.getPlannedRoom()).as("last non-blank room wins").isEqualTo("202");
        assertThat(examUser.getPlannedSeat()).as("blank seat in the later row does not clear the earlier one").isEqualTo("11");
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(student.getId(), course1.getId(), CourseRole.STUDENT)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterUserInExam_studentExamGenerated() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        exam.setStartDate(ZonedDateTime.now().minusMinutes(3));
        examRepository.save(exam);

        Set<StudentExam> studentExamsBefore = studentExamRepository.findByExamId(exam.getId());
        assertThat(studentExamsBefore).isEmpty();

        var student = new StudentDTO(OTHER_PREFIX + "student1", "", "", "", "");
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", List.of(student), HttpStatus.OK, null);

        Set<StudentExam> studentExamsAfter = studentExamRepository.findByExamId(exam.getId());
        assertThat(studentExamsAfter).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAddStudentToExam_testExam() throws Exception {
        var student = new StudentDTO(OTHER_PREFIX + "student1", "", "", "", "");
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", List.of(student), HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRemoveStudentToExam_testExam() throws Exception {
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students/" + OTHER_PREFIX + "student1", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterUsersInExam() throws Exception {
        var savedExam = examUtilService.addExam(course1);

        List<String> registrationNumbers = Arrays.asList("1111111", "1111112", "1111113");
        List<User> students = userUtilService.setRegistrationNumberOfStudents(registrationNumbers, TEST_PREFIX);

        User student1 = students.getFirst();
        User student2 = students.get(1);
        User student3 = students.get(2);

        var registrationNumber3WithTypo = "1111113" + "0";
        var registrationNumber4WithTypo = "1111115" + "1";
        var registrationNumber99 = "1111199";
        var registrationNumber111 = "1111100";
        var emptyRegistrationNumber = "";

        // mock the ldap service
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber3WithTypo);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(emptyRegistrationNumber);
        doReturn(Optional.empty()).when(ldapUserService).findByRegistrationNumber(registrationNumber4WithTypo);

        var ldapUser111Dto = new LdapUserDto().registrationNumber(registrationNumber111).firstName(STUDENT_111).lastName(STUDENT_111).login(STUDENT_111)
                .email(STUDENT_111 + "@tum.de");
        doReturn(Optional.of(ldapUser111Dto)).when(ldapUserService).findByRegistrationNumber(registrationNumber111);

        userUtilService.createAndSaveUser("student99"); // not registered for the course
        userUtilService.setRegistrationNumberOfUserAndSave("student99", registrationNumber99);

        User student99 = userTestRepository.findOneWithAuthoritiesByLogin("student99").orElseThrow();
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(student99.getId(), course1.getId(), CourseRole.STUDENT)).isFalse();

        var examUserDtoStudent1 = new StudentDTO(TEST_PREFIX + "student1", "", "", "", "");
        request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students", List.of(examUserDtoStudent1),
                ExamRegistrationResultDTO.class, HttpStatus.OK);
        var examUserDtoNonExisting = new StudentDTO("nonExistingStudent", "", "", "", "");
        var nonExistingResult = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students", List.of(examUserDtoNonExisting),
                ExamRegistrationResultDTO.class, HttpStatus.OK);
        assertThat(nonExistingResult.notFoundStudents()).hasSize(1);

        Exam storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();
        ExamUser examUserStudent1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).orElseThrow();
        assertThat(storedExam.getExamUsers()).containsExactly(examUserStudent1);

        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK);
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
        storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();
        assertThat(storedExam.getExamUsers()).isEmpty();

        var studentDto1 = UserFactory.generateStudentDTOWithRegistrationNumber(student1.getRegistrationNumber());
        var studentDto2 = UserFactory.generateStudentDTOWithRegistrationNumber(student2.getRegistrationNumber());
        var studentDto3 = new StudentDTO(student3.getLogin(), null, null, registrationNumber3WithTypo, null); // explicit typo, should be a registration failure later
        var studentDto4 = UserFactory.generateStudentDTOWithRegistrationNumber(registrationNumber4WithTypo); // explicit typo, should fall back to login name later
        var studentDto10 = UserFactory.generateStudentDTOWithRegistrationNumber(null); // completely empty

        var studentDto99 = new StudentDTO(student99.getLogin(), null, null, registrationNumber99, null);
        var studentDto111 = new StudentDTO(null, null, null, registrationNumber111, null);

        // Add a student with login but empty registration number
        var studentsToRegister = List.of(studentDto1, studentDto2, studentDto3, studentDto4, studentDto99, studentDto111, studentDto10);

        // now we register all these students for the exam.
        // all students get registered if they can be found in the LDAP
        ExamRegistrationResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students", studentsToRegister,
                ExamRegistrationResultDTO.class, HttpStatus.OK);
        assertThat(result.notFoundStudents()).extracting(ExamUserDTO::registrationNumber).containsExactlyInAnyOrder(registrationNumber4WithTypo, null);
        assertThat(result.rejectedStaffUsers()).isNullOrEmpty();
        // TODO check audit events stored properly

        storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();

        // now a new user student101 should exist
        var student111 = userUtilService.getUserByLogin(STUDENT_111);

        var examUser1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).orElseThrow();
        var examUser2 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student2.getId()).orElseThrow();
        var examUser3 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student3.getId()).orElseThrow();
        var examUser99 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student99.getId()).orElseThrow();
        var examUser111 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student111.getId()).orElseThrow();

        assertThat(storedExam.getExamUsers()).containsExactlyInAnyOrder(examUser1, examUser2, examUser3, examUser99, examUser111);

        for (var examUser : storedExam.getExamUsers()) {
            // all registered users must have access to the course
            assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(examUser.getUser().getId(), course1.getId(), CourseRole.STUDENT)).isTrue();
        }

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterLDAPUsersInExam() throws Exception {
        var savedExam = examUtilService.addExam(course1);
        String student100 = TEST_PREFIX + "student100";
        String student200 = TEST_PREFIX + "student200";
        String student300 = TEST_PREFIX + "student300";

        // setup mocks
        var ldapUser1Dto = new LdapUserDto().firstName(student100).lastName(student100).login(student100).registrationNumber("100000").email(student100 + "@tum.de");
        doReturn(Optional.of(ldapUser1Dto)).when(ldapUserService).findByLogin(student100);

        var ldapUser2Dto = new LdapUserDto().firstName(student200).lastName(student200).login(student200).registrationNumber("200000").email(student200 + "@tum.de");
        doReturn(Optional.of(ldapUser2Dto)).when(ldapUserService).findByAnyEmail(student200 + "@tum.de");

        var ldapUser3Dto = new LdapUserDto().firstName(student300).lastName(student300).login(student300).registrationNumber("3000000").email(student300 + "@tum.de");
        doReturn(Optional.of(ldapUser3Dto)).when(ldapUserService).findByRegistrationNumber("3000000");

        // user with login
        StudentDTO dto1 = new StudentDTO(student100, student100, student100, null, null);
        // user with email
        StudentDTO dto2 = new StudentDTO(null, student200, student200, null, student200 + "@tum.de");
        // user with registration number
        StudentDTO dto3 = new StudentDTO(null, student300, student300, "3000000", null);
        // user without anything
        StudentDTO dto4 = new StudentDTO(null, null, null, null, null);

        ExamRegistrationResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                List.of(dto1, dto2, dto3, dto4), ExamRegistrationResultDTO.class, HttpStatus.OK);
        assertThat(result.notFoundStudents()).extracting(ExamUserDTO::registrationNumber).containsExactly(dto4.registrationNumber());
        assertThat(result.rejectedStaffUsers()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAddStudentsToExam_testExam() throws Exception {
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", "1111111");

        StudentDTO studentDto1 = UserFactory.generateStudentDTOWithRegistrationNumber("1111111");
        List<StudentDTO> studentDTOS = List.of(studentDto1);
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", studentDTOS, StudentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRemoveAllStudentsFromExam_testExam() throws Exception {
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/students", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteStudentThatDoesNotExist() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/nonExistingStudent", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testAddAllRegisteredUsersToExam() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        Channel channel = examUtilService.addExamChannel(exam, "testchannel");

        // Remove student99 if they were accidentally enrolled by the prefix-based enrollment from a
        // previous @BeforeEach run (2nd-run persistence bug), so the baseline count is predictable.
        // Use addStudent (not createAndSaveUser) so student99 gets ROLE_USER authority — the
        // findAllByCourseIdAndCourseRolesInWithAuthorities query requires a LEFT JOIN FETCH on authorities.
        userUtilService.addStudent(TEST_PREFIX + "student99"); // not registered for the course
        User student99 = userUtilService.getUserByLogin(TEST_PREFIX + "student99");
        userUtilService.unenrollUserFromCourse(student99, course1);

        int numberOfStudentsInCourse = userCourseRoleTestRepository.findByCourse_IdAndRole(course1.getId(), CourseRole.STUDENT).size();

        userUtilService.enrollUserInCourse(student99, course1, CourseRole.STUDENT);
        userUtilService.setRegistrationNumberOfUserAndSave(student99, "1234");
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(student99.getId(), course1.getId(), CourseRole.STUDENT)).isTrue();

        var examUser99 = examUserRepository.findByExamIdAndUserId(exam.getId(), student99.getId());
        assertThat(examUser99).isEmpty();

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/register-course-students", null, HttpStatus.OK, null);

        exam = examRepository.findWithExamUsersById(exam.getId()).orElseThrow();
        examUser99 = examUserRepository.findByExamIdAndUserId(exam.getId(), student99.getId());

        // the course students + our custom student99
        assertThat(exam.getExamUsers()).hasSize(numberOfStudentsInCourse + 1);
        assertThat(exam.getExamUsers()).contains(examUser99.orElseThrow());
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam.getId());

        Channel channelFromDB = channelRepository.findChannelByExamId(exam.getId());
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getExam()).isEqualTo(exam);
        assertThat(channelFromDB.getName()).isEqualTo(channel.getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterCourseStudents_testExam() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/register-course-students", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testIsUserRegisteredForExam() {
        var examUser = new ExamUser();
        examUser.setExam(exam1);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        exam1.addExamUser(examUser);
        final var exam = examRepository.save(exam1);
        final var isUserRegistered = examRegistrationService.isUserRegisteredForExam(exam.getId(), student1.getId());
        final var isCurrentUserRegistered = examRegistrationService.isCurrentUserRegisteredForExam(exam.getId());
        assertThat(isUserRegistered).isTrue();
        assertThat(isCurrentUserRegistered).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterPlainStudentToExam_successful() throws Exception {
        var examUserDtoStudent1 = new StudentDTO(TEST_PREFIX + "student1", "", "", "", "");
        request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", List.of(examUserDtoStudent1),
                ExamRegistrationResultDTO.class, HttpStatus.OK);

        Exam storedExam = examRepository.findWithExamUsersById(exam1.getId()).orElseThrow();
        ExamUser examUser = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId()).orElseThrow();

        assertThat(storedExam.getExamUsers()).contains(examUser);
    }

    // ExamRegistration Service - checkRegistrationOrRegisterStudentToTestExam
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckRegistrationOrRegisterStudentToTestExam_noTestExam() {
        assertThatThrownBy(
                () -> examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, exam1.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1")))
                .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "student1", roles = "USER")
    void testCheckRegistrationOrRegisterStudentToTestExam_studentNotPartOfCourse() {
        assertThatThrownBy(
                () -> examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, exam1.getId(), userUtilService.getUserByLogin(OTHER_PREFIX + "student1")))
                .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckRegistrationOrRegisterStudentToTestExam_successfulRegistration() {
        Exam testExam = ExamFactory.generateTestExam(course1);
        testExam = examRepository.save(testExam);
        var examUser = new ExamUser();
        examUser.setExam(testExam);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        testExam.addExamUser(examUser);
        testExam = examRepository.save(testExam);
        examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course1, testExam.getId(), student1);
        Exam testExamReloaded = examRepository.findByIdWithExamUsersElseThrow(testExam.getId());
        assertThat(testExamReloaded.getExamUsers()).contains(examUser);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterStaffInExam_rejectedButRealStudentRegistered() throws Exception {
        var savedExam = examUtilService.addExam(course1);

        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", "2000001");
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "editor1", "2000002");
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "tutor1", "2000003");

        var studentDto = UserFactory.generateStudentDTOWithRegistrationNumber("2000001");
        var editorDto = UserFactory.generateStudentDTOWithRegistrationNumber("2000002");
        var tutorDto = UserFactory.generateStudentDTOWithRegistrationNumber("2000003");

        ExamRegistrationResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + savedExam.getId() + "/students",
                List.of(studentDto, editorDto, tutorDto), ExamRegistrationResultDTO.class, HttpStatus.OK);

        assertThat(result.rejectedStaffUsers()).extracting(ExamUserDTO::registrationNumber).containsExactlyInAnyOrder("2000002", "2000003");
        assertThat(result.notFoundStudents()).isNullOrEmpty();

        Exam storedExam = examRepository.findWithExamUsersById(savedExam.getId()).orElseThrow();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        assertThat(examUserRepository.findByExamIdAndUserId(storedExam.getId(), student.getId())).isPresent();
        assertThat(examUserRepository.findByExamIdAndUserId(storedExam.getId(), editor.getId())).isEmpty();
        assertThat(examUserRepository.findByExamIdAndUserId(storedExam.getId(), tutor.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRegisterCourseStudents_doesNotRegisterStaff() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam, "staff-filter-channel");

        // user holding BOTH the STUDENT and the EDITOR course role, so the student lookup returns them but they must be rejected as staff
        User dualRole = userUtilService.createAndSaveUser(TEST_PREFIX + "dualrole");
        userUtilService.enrollUserInCourse(dualRole, course1, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(dualRole, course1, CourseRole.EDITOR);

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/register-course-students", null, HttpStatus.OK, null);

        exam = examRepository.findWithExamUsersById(exam.getId()).orElseThrow();
        assertThat(examUserRepository.findByExamIdAndUserId(exam.getId(), dualRole.getId())).isEmpty();

        // a normal course student is still registered
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(examUserRepository.findByExamIdAndUserId(exam.getId(), student1.getId())).isPresent();
    }
}
