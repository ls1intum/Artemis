package de.tum.cit.aet.artemis.buildagent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Guards every way the clone token of a build job could escape to somewhere it does not belong.
 * <p>
 * The token is a live credential that travels inside {@link BuildJobQueueItem} so that the agent which claims the job
 * receives it. That is the only place it may go. Each exit below is closed by a different mechanism, and none of them
 * is visible at the point where a future change would break it, which is why each gets its own test:
 * <ul>
 * <li>REST and websocket payloads, closed by {@code @JsonIgnore}. {@code BuildJobQueueResource} returns this record
 * directly to instructors and admins for queued and running jobs.</li>
 * <li>Log output, closed by the overridden {@code toString}. Build jobs are logged whole at info level while they run,
 * which {@code @JsonIgnore} does not affect at all.</li>
 * <li>The database, closed by {@link BuildJob} copying named fields only.</li>
 * </ul>
 * The last test asserts the opposite direction: the token must survive Java serialization, or the agent never receives
 * it and every https clone silently falls back to the deprecated shared credential.
 */
class BuildJobCloneTokenLeakTest {

    private static final String CLONE_TOKEN = "bjct-a-token-that-must-not-escape";

    private static BuildJobQueueItem buildJobWithCloneToken() {
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, "http://localhost:8000/git/KEY/slug.git",
                "http://localhost:8000/git/KEY/tests.git", null, new String[0], new String[0]);
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, java.util.List.of(), 0, null, null, null,
                null);
        return new BuildJobQueueItem("job-1", "name", new BuildAgentDTO("agent-1", "address", "display"), 1L, 2L, 3L, 0, 1, BuildStatus.BUILDING, repositoryInfo, jobTimingInfo,
                buildConfig, null, CLONE_TOKEN);
    }

    @Test
    void shouldNotSerializeTheCloneTokenToJson() throws Exception {
        // JavaTimeModule as the application mapper has it, otherwise this fails on the job timing dates before it
        // ever reaches the token
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = objectMapper.writeValueAsString(buildJobWithCloneToken());

        assertThat(json).as("the clone token must never reach a REST or websocket payload; BuildJobQueueResource returns this record straight to instructors and admins")
                .doesNotContain(CLONE_TOKEN).doesNotContain("cloneToken");
    }

    @Test
    void shouldNotPrintTheCloneTokenInToString() {
        String printed = buildJobWithCloneToken().toString();

        assertThat(printed).as("build jobs are logged whole at info level, so a token in toString would land in every agent log and support bundle").doesNotContain(CLONE_TOKEN);
        assertThat(printed).as("the remaining fields must still be readable, the point is redaction rather than removal").contains("job-1").contains("agent-1");
    }

    @Test
    void shouldNotCopyTheCloneTokenIntoThePersistedBuildJob() {
        BuildJob persisted = new BuildJob(buildJobWithCloneToken(), BuildStatus.BUILDING, null);

        assertThat(Arrays.stream(BuildJob.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .as("the token is a live credential and has no business in the database, where it would outlive the build job").doesNotContain("cloneToken");
        assertThat(persisted.getBuildJobId()).isEqualTo("job-1");
    }

    /**
     * The inverse guard. Hazelcast transports this record with Java serialization, so if the token stopped surviving a
     * round trip the agent would silently fall back to the deprecated shared credential, or fail to clone where none
     * is configured. That failure would look like a configuration problem rather than a regression here.
     */
    @Test
    void shouldKeepTheCloneTokenThroughJavaSerialization() throws IOException, ClassNotFoundException {
        BuildJobQueueItem original = buildJobWithCloneToken();

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        BuildJobQueueItem restored;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BuildJobQueueItem) in.readObject();
        }

        assertThat(restored.cloneToken()).as("the agent receives the token through the distributed queue, which Hazelcast serializes with Java serialization")
                .isEqualTo(CLONE_TOKEN);
    }

    /**
     * A build job that has finished must not carry the token onwards: it leaves the processing list at that moment, so
     * the token stops being accepted, and this item travels on to the result queue and the finished job records.
     */
    @Test
    void shouldDropTheCloneTokenWhenTheJobFinishes() {
        BuildJobQueueItem finished = new BuildJobQueueItem(buildJobWithCloneToken(), ZonedDateTime.now(), BuildStatus.SUCCESSFUL);

        assertThat(finished.cloneToken()).isNull();
    }

    /**
     * The opposite requirement on the path that matters most: a job being handed to an agent keeps its token, because
     * the processing list entry is where a core node looks it up when that agent clones.
     */
    @Test
    void shouldKeepTheCloneTokenWhenTheJobStartsProcessing() {
        BuildJobQueueItem processing = new BuildJobQueueItem(buildJobWithCloneToken(), new BuildAgentDTO("agent-1", "address", "display"), ZonedDateTime.now());

        assertThat(processing.cloneToken()).isEqualTo(CLONE_TOKEN);
    }

    /**
     * A retry reuses the same build job id, so it must also reuse the token, or the second attempt cannot clone.
     */
    @Test
    void shouldKeepTheCloneTokenOnRetry() {
        BuildJobQueueItem retried = new BuildJobQueueItem(buildJobWithCloneToken(), new BuildAgentDTO("agent-2", "address", "display"), 1);

        assertThat(retried.cloneToken()).isEqualTo(CLONE_TOKEN);
    }
}
