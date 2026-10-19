package de.tum.cit.aet.artemis.buildagent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/**
 * Guards the Hazelcast wire compatibility of {@link BuildAgentDetailsDTO}.
 * <p>
 * The DTO is stored in a distributed map with plain Java serialization, and a rolling upgrade temporarily runs nodes of two versions side by side. Bumping the serial
 * version would make each version reject the other's entries with an {@code InvalidClassException}, so the build agent overview would break for the duration of every
 * upgrade.
 */
class BuildAgentDetailsDTOTest {

    private static final ZonedDateTime START_DATE = ZonedDateTime.parse("2026-08-01T12:00:00Z");

    @Test
    void keepsTheSerialVersionThatOlderNodesWrite() {
        // Deliberately pinned. Adding a record component does not require a new serial version, because record deserialization tolerates missing components on its own.
        assertThat(ObjectStreamClass.lookup(BuildAgentDetailsDTO.class).getSerialVersionUID()).isEqualTo(2L);
    }

    @Test
    void fillsInRunnerMetadataMissingFromAnOlderNodesEntry() throws Exception {
        // An entry written before the runner metadata existed deserializes with the default value for the added components, which is null for both.
        BuildAgentDetailsDTO writtenByOlderNode = new BuildAgentDetailsDTO(1, 2, 3, 4, 5, 6, START_DATE, START_DATE, "abc1234", 0, "27.0.1", null, null);

        BuildAgentDetailsDTO read = roundTrip(writtenByOlderNode);

        assertThat(read.buildRunner()).isEqualTo("Docker");
        assertThat(read.buildRunnerVersion()).isEqualTo("27.0.1");
        assertThat(read.dockerVersion()).isEqualTo("27.0.1");
        assertThat(read.consecutiveBuildFailures()).isZero();
    }

    @Test
    void reportsAnUnknownRunnerWhenTheOlderNodeHadNoDockerVersionEither() throws Exception {
        BuildAgentDetailsDTO writtenByOlderNode = new BuildAgentDetailsDTO(1, 2, 3, 4, 5, 6, null, START_DATE, null, 3, null, null, null);

        BuildAgentDetailsDTO read = roundTrip(writtenByOlderNode);

        assertThat(read.buildRunner()).isEqualTo("Unknown");
        assertThat(read.buildRunnerVersion()).isNull();
        assertThat(read.consecutiveBuildFailures()).isEqualTo(3);
    }

    @Test
    void keepsTheRunnerMetadataOfAnEntryThatCarriesIt() throws Exception {
        BuildAgentDetailsDTO writtenByCurrentNode = new BuildAgentDetailsDTO(1, 2, 3, 4, 5, 6, START_DATE, START_DATE, "abc1234", 0, null, "Kubernetes", "v1.34.0");

        assertThat(roundTrip(writtenByCurrentNode)).isEqualTo(writtenByCurrentNode);
    }

    @Test
    void derivesTheRunnerFromTheDockerVersionInTheCompatibilityConstructor() {
        assertThat(new BuildAgentDetailsDTO(1, 2, 3, 4, 5, 6, START_DATE, START_DATE, "abc1234", 0, "27.0.1").buildRunner()).isEqualTo("Docker");
        assertThat(new BuildAgentDetailsDTO(1, 2, 3, 4, 5, 6, START_DATE, START_DATE, "abc1234", 0, null).buildRunner()).isEqualTo("Unknown");
    }

    private static BuildAgentDetailsDTO roundTrip(BuildAgentDetailsDTO details) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(details);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (BuildAgentDetailsDTO) input.readObject();
        }
    }
}
