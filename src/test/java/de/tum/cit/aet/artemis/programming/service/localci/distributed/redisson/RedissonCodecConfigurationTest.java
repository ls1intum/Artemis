package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResultQueueException;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

class RedissonCodecConfigurationTest {

    @Test
    void roundTripWithQueueSafeException() throws Exception {
        ObjectMapper objectMapper = configuredMapper();
        ResultQueueItem resultQueueItem = new ResultQueueItem(null, null, List.of(),
                BuildResultQueueException.from(new CompletionException("build failed", new ExecutionException(new TimeoutException("timed out")))));

        String json = objectMapper.writeValueAsString(resultQueueItem);
        ResultQueueItem deserialized = objectMapper.readValue(json, ResultQueueItem.class);

        assertThat(deserialized.exception()).isInstanceOf(BuildResultQueueException.class);
        BuildResultQueueException exception = (BuildResultQueueException) deserialized.exception();
        assertThat(exception.getOriginalClassName()).isEqualTo(CompletionException.class.getName());
        assertThat(exception.getCause()).isInstanceOf(BuildResultQueueException.class);
        assertThat(((BuildResultQueueException) exception.getCause()).getOriginalClassName()).isEqualTo(ExecutionException.class.getName());
    }

    @Test
    void roundTripSuccessfulBuildResult() throws Exception {
        ObjectMapper objectMapper = configuredMapper();

        var successfulTests = List.of(new LocalCITestJobDTO("TestCompile", List.of()), new LocalCITestJobDTO("TestOutput", List.of()));
        var failedTests = List.of(new LocalCITestJobDTO("TestOutputLSan", List.of("Expected 'Hello world!' but received read ''")));
        var job = new LocalCIJobDTO(failedTests, successfulTests);
        var buildLogs = List.of(new BuildLogDTO(ZonedDateTime.now(), "Build started"), new BuildLogDTO(ZonedDateTime.now(), "Build finished"));

        var buildResult = new BuildResult("main", "abc123", "def456", true, ZonedDateTime.now(), List.of(job), buildLogs, List.of(), true);

        var buildAgent = new BuildAgentDTO("artemis-build-agent-3", "artemis-1003", "Artemis Build Agent 3");
        var repoInfo = new RepositoryInfo("test-repo", null, null, "https://example.com/assignment.git", "https://example.com/test.git", "https://example.com/solution.git", null,
                null);
        var timingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), null, 0L);
        var buildConfig = new BuildConfig("#!/bin/bash\necho test", "docker-image:latest", null, null, null, "main", ProgrammingLanguage.C, ProjectType.PLAIN, false, false,
                List.of("results"), 120, null, null, null, null);
        var buildJob = new BuildJobQueueItem("job-123", "Test Build", buildAgent, 1L, 1L, 1L, 0, 5, BuildStatus.SUCCESSFUL, repoInfo, timingInfo, buildConfig, null);

        var resultQueueItem = new ResultQueueItem(buildResult, buildJob, buildLogs, null);

        String json = objectMapper.writeValueAsString(resultQueueItem);
        ResultQueueItem deserialized = objectMapper.readValue(json, ResultQueueItem.class);

        // Verify BuildResult test data survives round-trip
        assertThat(deserialized.buildResult()).isNotNull().isInstanceOf(BuildResult.class);
        assertThat(deserialized.buildResult().isBuildSuccessful()).isTrue();
        assertThat(deserialized.buildResult().assignmentRepoCommitHash()).isEqualTo("abc123");
        assertThat(deserialized.buildResult().jobs()).hasSize(1);
        assertThat(deserialized.buildResult().jobs().getFirst().failedTests()).hasSize(1);
        assertThat(deserialized.buildResult().jobs().getFirst().failedTests().getFirst().testMessages()).containsExactly("Expected 'Hello world!' but received read ''");
        assertThat(deserialized.buildResult().jobs().getFirst().successfulTests()).hasSize(2);
        assertThat(deserialized.buildResult().jobs().getFirst().successfulTests().getFirst().testMessages()).isNotNull().isEmpty();
        assertThat(deserialized.buildResult().buildLogEntries()).hasSize(2);

        // Verify BuildJobQueueItem
        assertThat(deserialized.buildJobQueueItem()).isNotNull().isInstanceOf(BuildJobQueueItem.class);
        assertThat(deserialized.buildJobQueueItem().status()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(deserialized.buildJobQueueItem().buildAgent().name()).isEqualTo("artemis-build-agent-3");

        // Verify no exception for successful build
        assertThat(deserialized.exception()).isNull();
    }

    @Test
    void roundTripEmptyTestMessagesNotNull() throws Exception {
        ObjectMapper objectMapper = configuredMapper();

        // Successful test with empty testMessages — this is the common case
        var testJob = new LocalCITestJobDTO("TestCompile", List.of());

        // Serialize as Object.class like Redisson does internally
        String json = objectMapper.writerFor(Object.class).writeValueAsString(testJob);
        Object raw = objectMapper.readValue(json, Object.class);
        LocalCITestJobDTO deserialized = objectMapper.convertValue(raw, LocalCITestJobDTO.class);

        assertThat(deserialized.name()).isEqualTo("TestCompile");
        assertThat(deserialized.testMessages()).isNotNull().isEmpty();
    }

    private static ObjectMapper configuredMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        RedissonCodecConfiguration.configureObjectMapper(objectMapper);
        RedissonCodecConfiguration.configureTypeInclusion(objectMapper);
        return objectMapper;
    }
}
