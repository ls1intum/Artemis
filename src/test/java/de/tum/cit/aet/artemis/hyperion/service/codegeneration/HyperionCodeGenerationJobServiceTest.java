package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class HyperionCodeGenerationJobServiceTest {

    private static final String JOB_MAP_NAME = "hyperion-code-generation-jobs";

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private HyperionCodeGenerationTaskService taskService;

    @Mock
    private IMap<String, HyperionCodeGenerationJobService.JobInfo> jobMap;

    @Mock
    private Config hazelcastConfig;

    @Mock
    private MapConfig mapConfig;

    private HyperionCodeGenerationJobService service;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new HyperionCodeGenerationJobService(hazelcastInstance, taskService);
        when(hazelcastInstance.<String, HyperionCodeGenerationJobService.JobInfo>getMap(JOB_MAP_NAME)).thenReturn(jobMap);
        when(hazelcastInstance.getConfig()).thenReturn(hazelcastConfig);
        when(hazelcastConfig.getMapConfig(JOB_MAP_NAME)).thenReturn(mapConfig);
        service.init();

        user = new User();
        user.setLogin("testuser");

        exercise = new ProgrammingExercise();
        exercise.setId(42L);
    }

    @Test
    void init_setsMapTtl() {
        verify(mapConfig).setTimeToLiveSeconds(3600);
    }

    @Test
    void startJob_withNewJob_runsTaskAndReturnsJobId() {
        when(jobMap.putIfAbsent(eq("42"), any(HyperionCodeGenerationJobService.JobInfo.class))).thenReturn(null);

        String jobId = service.startJob(user, exercise, RepositoryType.SOLUTION);

        ArgumentCaptor<HyperionCodeGenerationJobService.JobInfo> jobCaptor = ArgumentCaptor.forClass(HyperionCodeGenerationJobService.JobInfo.class);
        verify(jobMap).putIfAbsent(eq("42"), jobCaptor.capture());
        HyperionCodeGenerationJobService.JobInfo createdJob = jobCaptor.getValue();

        assertThat(createdJob.userLogin()).isEqualTo("testuser");
        assertThat(createdJob.exerciseId()).isEqualTo(42L);
        assertThat(createdJob.repositoryType()).isEqualTo(RepositoryType.SOLUTION);
        assertThat(jobId).isEqualTo(createdJob.jobId());
        verify(taskService).runJobAsync(eq(jobId), eq(user), eq(exercise), eq(RepositoryType.SOLUTION), any(Runnable.class));
    }

    @Test
    void startJob_withExistingJobForSameUser_returnsExistingId() {
        HyperionCodeGenerationJobService.JobInfo existingJob = new HyperionCodeGenerationJobService.JobInfo("job-1", "testuser", 42L, RepositoryType.SOLUTION, Instant.now());
        when(jobMap.putIfAbsent(eq("42"), any(HyperionCodeGenerationJobService.JobInfo.class))).thenReturn(existingJob);

        String jobId = service.startJob(user, exercise, RepositoryType.SOLUTION);

        assertThat(jobId).isEqualTo("job-1");
        verify(taskService, never()).runJobAsync(eq("job-1"), eq(user), eq(exercise), eq(RepositoryType.SOLUTION), any(Runnable.class));
    }

    @Test
    void startJob_withExistingJobForOtherUser_throwsConflict() {
        HyperionCodeGenerationJobService.JobInfo existingJob = new HyperionCodeGenerationJobService.JobInfo("job-2", "other", 42L, RepositoryType.TEMPLATE, Instant.now());
        when(jobMap.putIfAbsent(eq("42"), any(HyperionCodeGenerationJobService.JobInfo.class))).thenReturn(existingJob);

        assertThatThrownBy(() -> service.startJob(user, exercise, RepositoryType.TEMPLATE)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Code generation already running for this exercise");
    }

    @Test
    void getActiveJob_withMatchingUser_returnsJob() {
        HyperionCodeGenerationJobService.JobInfo existingJob = new HyperionCodeGenerationJobService.JobInfo("job-3", "testuser", 42L, RepositoryType.TESTS, Instant.now());
        when(jobMap.get("42")).thenReturn(existingJob);

        Optional<HyperionCodeGenerationJobService.JobInfo> result = service.getActiveJob(user, exercise);

        assertThat(result).contains(existingJob);
    }

    @Test
    void getActiveJob_withDifferentUser_returnsEmpty() {
        HyperionCodeGenerationJobService.JobInfo existingJob = new HyperionCodeGenerationJobService.JobInfo("job-4", "other", 42L, RepositoryType.SOLUTION, Instant.now());
        when(jobMap.get("42")).thenReturn(existingJob);

        Optional<HyperionCodeGenerationJobService.JobInfo> result = service.getActiveJob(user, exercise);

        assertThat(result).isEmpty();
    }

    @Test
    void clearJob_withMatchingId_removesEntry() {
        HyperionCodeGenerationJobService.JobInfo existingJob = new HyperionCodeGenerationJobService.JobInfo("job-5", "testuser", 42L, RepositoryType.SOLUTION, Instant.now());
        when(jobMap.get("42")).thenReturn(existingJob);

        ReflectionTestUtils.invokeMethod(service, "clearJob", 42L, "job-5");

        verify(jobMap).remove("42");
    }

    @Test
    void clearJob_withDifferentId_keepsEntry() {
        HyperionCodeGenerationJobService.JobInfo existingJob = new HyperionCodeGenerationJobService.JobInfo("job-6", "testuser", 42L, RepositoryType.SOLUTION, Instant.now());
        when(jobMap.get("42")).thenReturn(existingJob);

        ReflectionTestUtils.invokeMethod(service, "clearJob", 42L, "other-job");

        verify(jobMap, never()).remove("42");
    }
}
