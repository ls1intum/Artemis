package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationCapabilityService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/** Exercises the service's repository boundaries with real InnoDB snapshots; PostgreSQL integration covers the full import. */
class GenerationVariantDraftServiceMySqlTest {

    @Test
    void assignmentCommittedWhileDraftWaitsCannotBeHiddenByARepeatableReadSnapshot() throws Exception {
        try (var mysql = new GenericContainer<>(DockerImageName.parse("mysql:" + System.getProperty("mysql.version"))).withEnv("MYSQL_ROOT_PASSWORD", "test")
                .withEnv("MYSQL_ROOT_HOST", "%").withEnv("MYSQL_DATABASE", "authoring").withExposedPorts(3306)) {
            mysql.start();
            var dataSource = new DriverManagerDataSource(
                    "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/authoring?allowPublicKeyRetrieval=true&useSSL=false", "root", "test");
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE exam (id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE exercise (id BIGINT PRIMARY KEY, exam_id BIGINT) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE student_exam (id BIGINT PRIMARY KEY, exam_id BIGINT) ENGINE=InnoDB");
            jdbc.update("INSERT INTO exam VALUES (1)");
            jdbc.update("INSERT INTO exercise VALUES (1, 1)");
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            var exercises = mock(ProgrammingExerciseTestRepository.class);
            var exams = mock(ExamRepositoryApi.class);
            var capabilities = mock(GenerationCapabilityService.class);
            var imports = mock(ProgrammingExerciseImportService.class);
            var buildConfigs = mock(ProgrammingExerciseBuildConfigRepository.class);
            var exam = new Exam();
            exam.setId(1L);
            var group = new ExerciseGroup();
            group.setExam(exam);
            var source = new ProgrammingExercise();
            source.setId(1L);
            source.setTitle("Source");
            source.setExerciseGroup(group);
            source.setMaxPoints(10.0);
            when(exercises.findExamIdById(1L)).thenAnswer(invocation -> Optional.of(jdbc.queryForObject("SELECT exam_id FROM exercise WHERE id = 1", Long.class)));
            when(exercises.prepareAuthoringDraft(any())).thenAnswer(invocation -> transaction.execute(status -> invocation.<Supplier<Object>>getArgument(0).get()));
            when(exams.withExerciseSelectionLock(eq(1L), any())).thenAnswer(invocation -> {
                jdbc.queryForObject("SELECT id FROM exam WHERE id = 1 FOR UPDATE", Long.class);
                assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
                return invocation.<Function<Exam, Object>>getArgument(1).apply(exam);
            });
            when(exercises.findForAuthoringImportById(1L)).thenAnswer(invocation -> {
                jdbc.queryForObject("SELECT id FROM exercise WHERE id = 1", Long.class);
                return Optional.of(source);
            });
            doAnswer(invocation -> {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM student_exam WHERE exam_id = 1", Integer.class) > 0) {
                    throw new BadRequestAlertException("Exam already assigned", "programmingExercise", "examAlreadyAssigned");
                }
                return null;
            }).when(capabilities).requireMutable(source);
            when(buildConfigs.getProgrammingExerciseBuildConfigElseThrow(1L)).thenReturn(new ProgrammingExerciseBuildConfig());
            when(imports.prepareImport(eq(source), any(), any(), isNull())).thenAnswer(invocation -> {
                jdbc.update("INSERT INTO exercise VALUES (2, 1)");
                ProgrammingExercise draft = invocation.getArgument(2);
                draft.setId(2L);
                return draft;
            });
            var drafts = new GenerationVariantDraftService(exercises, buildConfigs, imports, capabilities, mock(TeamAssignmentConfigRepository.class), Optional.of(exams));
            var ready = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var holderConnection = new AtomicLong();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var assignment = executor.submit(() -> transaction.execute(status -> {
                    jdbc.queryForObject("SELECT id FROM exam WHERE id = 1 FOR UPDATE", Long.class);
                    jdbc.update("INSERT INTO student_exam VALUES (1, 1)");
                    holderConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                    ready.countDown();
                    try {
                        assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    return null;
                }));
                assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
                var draft = executor
                        .submit(() -> drafts.prepare(1L, new VariantGenerationRequestDTO(DifficultyLevel.HARD, "Library", null, null, null), ProgrammingExercise::getId));
                try {
                    await().atMost(Duration.ofSeconds(20)).until(() -> jdbc.queryForObject("""
                            SELECT COUNT(*) FROM performance_schema.data_lock_waits waits
                            JOIN performance_schema.threads holder ON holder.THREAD_ID = waits.BLOCKING_THREAD_ID
                            WHERE holder.PROCESSLIST_ID = ?
                            """, Integer.class, holderConnection.get()) > 0);
                    assertThat(draft.isDone()).isFalse();
                }
                finally {
                    release.countDown();
                }
                assignment.get(20, TimeUnit.SECONDS);
                assertThatThrownBy(() -> draft.get(20, TimeUnit.SECONDS)).hasRootCauseInstanceOf(BadRequestAlertException.class);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise", Integer.class)).isEqualTo(1);
                verify(imports, never()).prepareImport(any(), any(), any(), any());
            }
            finally {
                release.countDown();
            }
        }
    }
}
