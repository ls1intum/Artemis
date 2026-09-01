package de.tum.cit.aet.artemis.buildagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Verifies that {@link BuildJobQueueItem}, which is shared across core and build agent nodes, round-trips both the Java
 * serialization used by Hazelcast and the JSON serialization, in particular the container identity fields added for
 * multi-container build plans.
 */
class BuildJobQueueItemTest {

    private static BuildJobQueueItem jobWithIdentity(Long submissionId, String containerName) {
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, "https://example.com/assignment.git", "https://example.com/tests.git",
                "https://example.com/solution.git", new String[] {}, new String[] {});
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, false, false,
                List.of("results/*.xml"), 15, "assignmentPath", "testPath", "solutionPath", null);
        return new BuildJobQueueItem("id", "name", null, 1, 1, 1, 0, 0, null, repositoryInfo, jobTimingInfo, buildConfig, null, submissionId, containerName, null);
    }

    @Test
    void testJavaSerializationRoundTripKeepsContainerIdentity() throws Exception {
        var original = jobWithIdentity(42L, "student_tests");

        var out = new ByteArrayOutputStream();
        try (var objectOut = new ObjectOutputStream(out)) {
            objectOut.writeObject(original);
        }
        final BuildJobQueueItem deserialized;
        try (var objectIn = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            deserialized = (BuildJobQueueItem) objectIn.readObject();
        }

        // record equals() compares the String[] arrays in RepositoryInfo by reference, so a plain isEqualTo would fail
        // after any round-trip; a recursive comparison checks the whole object (including the new identity fields) by value
        assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
        assertThat(deserialized.submissionId()).isEqualTo(42L);
        assertThat(deserialized.containerName()).isEqualTo("student_tests");
    }

    @Test
    void testJsonSerializationRoundTripKeepsContainerIdentity() throws Exception {
        var mapper = JsonObjectMapper.get();
        var original = jobWithIdentity(42L, "student_tests");

        var deserialized = mapper.readValue(mapper.writeValueAsString(original), BuildJobQueueItem.class);

        assertThat(deserialized.submissionId()).isEqualTo(42L);
        assertThat(deserialized.containerName()).isEqualTo("student_tests");
    }

    @Test
    void testNullContainerIdentityStillRoundTrips() throws Exception {
        // a build plan without containers builds the whole submission, so both fields are null
        var mapper = JsonObjectMapper.get();
        var original = jobWithIdentity(null, null);

        var deserialized = mapper.readValue(mapper.writeValueAsString(original), BuildJobQueueItem.class);

        assertThat(deserialized.submissionId()).isNull();
        assertThat(deserialized.containerName()).isNull();
    }
}
