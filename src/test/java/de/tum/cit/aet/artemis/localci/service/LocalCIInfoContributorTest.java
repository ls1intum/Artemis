package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.info.Info;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;

/**
 * Unit tests for the build configuration the client reads from the info endpoint.
 * <p>
 * The exercise editor builds its build-timeout slider and its Docker flag hints from these values, so what is published
 * here is what an instructor is offered. The memory flags are the interesting part: Docker states them as strings with a
 * unit, and the client expects megabytes, so every unit has to be converted rather than passed through.
 */
@ExtendWith(MockitoExtension.class)
class LocalCIInfoContributorTest {

    @Mock
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private LocalCIInfoContributor localCIInfoContributor;

    @BeforeEach
    void setUp() {
        localCIInfoContributor = new LocalCIInfoContributor(programmingLanguageConfiguration);
        ReflectionTestUtils.setField(localCIInfoContributor, "minInstructorBuildTimeoutOption", 10);
        ReflectionTestUtils.setField(localCIInfoContributor, "maxInstructorBuildTimeoutOption", 240);
        ReflectionTestUtils.setField(localCIInfoContributor, "defaultInstructorBuildTimeoutOption", 120);
        ReflectionTestUtils.setField(localCIInfoContributor, "networks", List.of("artemis-build-network"));
    }

    private Info contribute(List<String> defaultDockerFlags) {
        when(programmingLanguageConfiguration.getDefaultDockerFlags()).thenReturn(defaultDockerFlags);
        Info.Builder builder = new Info.Builder();
        localCIInfoContributor.contribute(builder);
        return builder.build();
    }

    @Test
    void contribute_publishesTheBuildTimeoutBoundsAndTheAllowedNetworks() {
        Info info = contribute(List.of());

        assertThat(info.getDetails()).as("the client is told which continuous integration system is in use").containsEntry(Constants.CONTINUOUS_INTEGRATION_NAME, "Local CI");
        // These three bound the timeout an instructor can choose in the exercise editor.
        assertThat(info.getDetails()).containsEntry(Constants.INSTRUCTOR_BUILD_TIMEOUT_MIN_OPTION, 10).containsEntry(Constants.INSTRUCTOR_BUILD_TIMEOUT_MAX_OPTION, 240)
                .containsEntry(Constants.INSTRUCTOR_BUILD_TIMEOUT_DEFAULT_OPTION, 120);
        assertThat(info.getDetails()).as("the networks an exercise may attach to are published").containsEntry(Constants.DOCKER_FLAG_ALLOWED_CUSTOM_NETWORKS,
                List.of("artemis-build-network"));
    }

    @Test
    void contribute_reportsTheDefaultContainerLimitsInMegabytes() {
        // Docker states memory with a unit; the client renders megabytes, so gigabytes have to be converted rather than published as the bare number.
        Info info = contribute(List.of("--cpus", "\"2\"", "--memory", "\"2g\"", "--memory-swap", "\"3g\""));

        assertThat(info.getDetails()).as("the CPU count is published as a number").containsEntry(Constants.DOCKER_FLAG_CPUS, 2L);
        assertThat(info.getDetails()).as("2g is published as 2048 MB").containsEntry(Constants.DOCKER_FLAG_MEMORY_MB, 2048L);
        assertThat(info.getDetails()).as("3g of swap is published as 3072 MB").containsEntry(Constants.DOCKER_FLAG_MEMORY_SWAP_MB, 3072L);
    }

    @Test
    void contribute_convertsMegabyteAndKilobyteMemoryFlags() {
        Info info = contribute(List.of("--memory", "\"512m\"", "--memory-swap", "\"2048k\""));

        assertThat(info.getDetails()).as("a value already given in megabytes is published unchanged").containsEntry(Constants.DOCKER_FLAG_MEMORY_MB, 512L);
        assertThat(info.getDetails()).as("2048k is published as the 2 MB it is").containsEntry(Constants.DOCKER_FLAG_MEMORY_SWAP_MB, 2L);
    }

    @Test
    void contribute_acceptsAMemoryFlagWithoutAUnit() {
        // A flag configured without a unit is already a plain megabyte count. It still arrives quoted, because ProgrammingLanguageConfiguration quotes every flag value so
        // that values containing spaces are not split, so the quotes have to be stripped rather than parsed as part of the number.
        Info info = contribute(List.of("--memory", "\"1024\""));

        assertThat(info.getDetails()).containsEntry(Constants.DOCKER_FLAG_MEMORY_MB, 1024L);
    }

    @Test
    void contribute_ignoresDockerFlagsTheClientDoesNotRender() {
        Info info = contribute(List.of("--pids-limit", "\"100\"", "--memory", "\"1g\""));

        assertThat(info.getDetails()).as("an unrelated flag does not appear in the published information").doesNotContainKey("--pids-limit");
        assertThat(info.getDetails()).as("the flags the client does render are still published").containsEntry(Constants.DOCKER_FLAG_MEMORY_MB, 1024L);
    }

    @Test
    void contribute_leavesOutAMemoryValueThatIsNotAWholeAmount() {
        // Stripping everything but the digits would turn a misconfigured -1024 into a limit of 1024 MB, which the exercise editor would then offer as the default. Throwing
        // is not an option either, because this builds the info endpoint the client needs to render anything at all.
        Info info = contribute(List.of("--memory", "\"-1024\"", "--memory-swap", "\"not a number\"", "--cpus", "\"2\""));

        assertThat(info.getDetails()).as("a negative memory value is not published as a positive limit").doesNotContainKey(Constants.DOCKER_FLAG_MEMORY_MB);
        assertThat(info.getDetails()).as("a value that is not a number at all is left out as well").doesNotContainKey(Constants.DOCKER_FLAG_MEMORY_SWAP_MB);
        assertThat(info.getDetails()).as("the flags that are configured properly are still published").containsEntry(Constants.DOCKER_FLAG_CPUS, 2L);
    }

    @Test
    void contribute_leavesOutACpuCountThatIsNotAWholeAmount() {
        Info info = contribute(List.of("--cpus", "\"-2\""));

        assertThat(info.getDetails()).doesNotContainKey(Constants.DOCKER_FLAG_CPUS);
    }

    @Test
    void contribute_leavesOutAMemoryValueWithAUnitButNoAmount() {
        Info info = contribute(List.of("--memory", "\"-2g\""));

        assertThat(info.getDetails()).as("the sign is not silently dropped from a value with a unit either").doesNotContainKey(Constants.DOCKER_FLAG_MEMORY_MB);
    }
}
