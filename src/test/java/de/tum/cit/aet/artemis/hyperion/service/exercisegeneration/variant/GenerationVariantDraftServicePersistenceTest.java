package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.StudentExamPreparationService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmittedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@WithMockUser(username = "hypvariantdraftinstructor1", roles = "INSTRUCTOR")
class GenerationVariantDraftServicePersistenceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private GenerationVariantDraftService drafts;

    @Autowired
    private ProgrammingExerciseUtilService programmingExercises;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository configurations;

    @Value("${artemis.version-control.default-branch}")
    private String defaultBranch;

    @Autowired
    private TeamAssignmentConfigRepository teamConfigurations;

    @Autowired
    private ExamTestRepository exams;

    @Autowired
    private ExamUtilService examUtil;

    @Autowired
    private StudentExamPreparationService assignment;

    @Autowired
    private DataSource dataSource;

    private ProgrammingExercise source;

    private final VariantGenerationRequestDTO request = new VariantGenerationRequestDTO(DifficultyLevel.HARD, "Library", null, null, null);

    @BeforeEach
    void setup() {
        userUtilService.addUsers("hypvariantdraft", 0, 0, 0, 1);
        var course = programmingExercises.addCourseWithOneProgrammingExercise();
        userUtilService.addInstructorToCourse("hypvariantdraftinstructor1", course);
        source = (ProgrammingExercise) course.getExercises().iterator().next();
        source.setProjectType(ProjectType.PLAIN_GRADLE);
        source.setStaticCodeAnalysisEnabled(false);
        programmingExerciseRepository.save(source);
        var configuration = configurations.getProgrammingExerciseBuildConfigElseThrow(source.getId());
        configuration.setSequentialTestRuns(false);
        configuration.setBranch("teaching");
        configurations.save(configuration);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void examDraftAndStudentAssignmentHoldTheSameLockThroughCommitInEitherOrder(boolean draftFirst) throws Exception {
        userUtilService.addUsers("hypvariantexam", 1, 0, 0, 0);
        Exam exam = examUtil.addExamWithExerciseGroup(source.getCourseViaExerciseGroupOrCourseMember(), true);
        exam.setStartDate(ZonedDateTime.now().plusDays(2));
        exam.setVisibleDate(ZonedDateTime.now().plusDays(1));
        exam.setEndDate(ZonedDateTime.now().plusDays(3));
        exam = examUtil.registerUsersForExamAndSaveExam(exam, "hypvariantexam", 1);
        source.setCourse(null);
        source.setExerciseGroup(exam.getExerciseGroups().iterator().next());
        programmingExerciseRepository.save(source);
        long examId = exam.getId();
        long exerciseCount = programmingExerciseRepository.count();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        AtomicInteger holderPid = new AtomicInteger();
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                if (draftFirst) {
                    return drafts.prepare(source.getId(), request, destination -> {
                        programmingExerciseRepository.flush();
                        holdExamTransaction(jdbc, holderPid, holderReady, releaseHolder);
                        return destination.getId();
                    });
                }
                return exams.withExerciseSelectionLock(examId, locked -> {
                    assignment.assignRegisteredStudents(examId, false);
                    holdExamTransaction(jdbc, holderPid, holderReady, releaseHolder);
                    return 0L;
                });
            });
            assertThat(holderReady.await(20, TimeUnit.SECONDS)).isTrue();
            var second = executor
                    .submit(() -> draftFirst ? assignment.assignRegisteredStudents(examId, false) : drafts.prepare(source.getId(), request, destination -> destination.getId()));
            try {
                // Observe PostgreSQL's actual waiter, rather than inferring lock acquisition from thread submission or elapsed time.
                await().atMost(Duration.ofSeconds(20))
                        .until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))", Integer.class, holderPid.get()) > 0);
                assertThat(second.isDone()).isFalse();
            }
            finally {
                releaseHolder.countDown();
            }
            long destinationId = first.get(20, TimeUnit.SECONDS);
            if (draftFirst) {
                assertThat(second.get(20, TimeUnit.SECONDS)).isInstanceOf(java.util.List.class);
                assertThat(exams.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId).getExerciseGroups()).singleElement()
                        .satisfies(group -> assertThat(group.getExercises()).extracting(exercise -> exercise.getId()).containsExactlyInAnyOrder(source.getId(), destinationId));
            }
            else {
                assertThatThrownBy(() -> second.get(20, TimeUnit.SECONDS)).hasRootCauseInstanceOf(BadRequestAlertException.class);
                assertThat(programmingExerciseRepository.count()).isEqualTo(exerciseCount);
            }
        }
        finally {
            releaseHolder.countDown();
        }
    }

    private static void holdExamTransaction(JdbcTemplate jdbc, AtomicInteger holderPid, CountDownLatch holderReady, CountDownLatch releaseHolder) {
        holderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
        holderReady.countDown();
        try {
            assertThat(releaseHolder.await(30, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while holding the exam transaction", exception);
        }
    }

    @Test
    void realImportStaysInvisibleUntilReservationReturnsAndTransactionCommits() {
        source.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(source);
        var teamConfig = teamConfigurations.findByExerciseId(source.getId()).orElseThrow();
        teamConfig.setMinTeamSize(2);
        teamConfig.setMaxTeamSize(4);
        teamConfigurations.save(teamConfig);
        source.setProblemStatement("Solve the tasks.");
        var sourceTestCases = programmingExercises.addTestCasesToProgrammingExercise(source);
        source.setTestCases(new HashSet<>(sourceTestCases));
        programmingExercises.addTasksToProgrammingExercise(source);
        var result = drafts.prepare(source.getId(), request, destination -> {
            programmingExerciseRepository.flush();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executor.submit(() -> programmingExerciseRepository.existsById(destination.getId())).get(20, TimeUnit.SECONDS)).isFalse();
            }
            catch (Exception exception) {
                throw new AssertionError("Could not read the uncommitted destination through a separate database connection", exception);
            }
            return destination.getId();
        });
        var destination = programmingExerciseRepository.findByIdElseThrow(result);
        assertThat(destination.getId()).isNotEqualTo(source.getId());
        assertThat(destination.getDifficulty()).isEqualTo(DifficultyLevel.HARD);
        var imported = programmingExerciseRepository.findForAuthoringImportById(result).orElseThrow();
        assertThat(imported.getTestCases()).hasSize(3).extracting(ProgrammingExerciseTestCase::getId)
                .doesNotContainAnyElementsOf(sourceTestCases.stream().map(ProgrammingExerciseTestCase::getId).toList());
        assertThat(imported.getTasks()).hasSize(3).flatExtracting(ProgrammingExerciseTask::getTestCases)
                .allSatisfy(testCase -> assertThat(testCase.getExercise().getId()).isEqualTo(result));
        var unchangedSource = programmingExerciseRepository.findForAuthoringImportById(source.getId()).orElseThrow();
        assertThat(unchangedSource.getTestCases()).hasSize(3);
        assertThat(unchangedSource.getTasks()).hasSize(3);
        var destinationTeamConfig = teamConfigurations.findByExerciseId(result).orElseThrow();
        assertThat(destinationTeamConfig.getMinTeamSize()).isEqualTo(2);
        assertThat(destinationTeamConfig.getMaxTeamSize()).isEqualTo(4);
        assertThat(teamConfigurations.findByExerciseId(source.getId()).orElseThrow().getMaxTeamSize()).isEqualTo(4);
        assertThat(destination.getReleaseDate()).isAfter(java.time.ZonedDateTime.now());
        assertThat(configurations.getProgrammingExerciseBuildConfigElseThrow(result).getBranch()).isEqualTo(defaultBranch);
        assertThat(configurations.getProgrammingExerciseBuildConfigElseThrow(source.getId()).getBranch()).isEqualTo("teaching");
        assertThat(programmingExerciseRepository.findByIdElseThrow(source.getId()).getTitle()).isEqualTo(source.getTitle());
    }

    @Test
    void activityStoreFailureAbortsAdmissionBeforeTheDraftCommits() {
        var destinationId = new AtomicLong();
        var rejected = new java.util.concurrent.atomic.AtomicReference<String>();
        var storeFailure = new IllegalStateException("activity store unavailable");
        var jobs = new GenerationJobService(new de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService(), event -> {
            if (event instanceof GenerationAdmittedEvent admitted) {
                rejected.set(admitted.run().jobId());
                throw storeFailure;
            }
            if (event instanceof GenerationStartedEvent) {
                throw new AssertionError("An untracked variant must never dispatch");
            }
        }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, java.time.Duration.ofMinutes(35), java.time.Duration.ofMinutes(30), Runnable::run);
        jobs.init();
        var user = userTestRepository.findOneByLogin("hypvariantdraftinstructor1").orElseThrow();

        assertThatThrownBy(() -> drafts.prepare(source.getId(), request, destination -> {
            destinationId.set(destination.getId());
            return jobs.prepareVariantJob(user, destination, "Variant", null, null, null, new GenerationVariantPreparation(source.getId(), "source", request));
        })).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class).hasRootCauseMessage("activity store unavailable");

        assertThat(rejected.get()).isNotBlank();
        assertThat(destinationId.get()).isPositive();
        assertThat(programmingExerciseRepository.existsById(destinationId.get())).isFalse();
        assertThat(configurations.findById(destinationId.get())).isEmpty();
        assertThat(jobs.hasActiveJob(destinationId.get())).isFalse();
    }

    @Test
    void reservationFailureRollsBackTheEntireImportedDatabaseGraph() {
        var destinationId = new AtomicLong();
        var rejection = new RuntimeException("reservation rejected");
        assertThatThrownBy(() -> drafts.prepare(source.getId(), request, destination -> {
            destinationId.set(destination.getId());
            programmingExerciseRepository.flush();
            throw rejection;
        })).isSameAs(rejection);

        assertThat(destinationId.get()).isPositive();
        assertThat(programmingExerciseRepository.existsById(destinationId.get())).isFalse();
        assertThat(configurations.findById(destinationId.get())).isEmpty();
    }
}
