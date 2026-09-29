package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDockerFlagsDTO;

/**
 * How the flags a container sets for its own job resolve against the exercise's flags.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseBuildConfigServiceTest {

    private static final String EXERCISE_FLAGS = "{\"network\":\"\",\"env\":{\"SHARED\":\"exercise\",\"MODE\":\"exercise\"},\"cpuCount\":2,\"memory\":1024,\"memorySwap\":0}";

    @Mock
    private LicenseService licenseService;

    private ProgrammingExerciseBuildConfigService service;

    private final ProgrammingExercise exercise = new ProgrammingExercise();

    @BeforeEach
    void setUp() {
        service = new ProgrammingExerciseBuildConfigService(licenseService);
        ReflectionTestUtils.setField(service, "allowedNetworks", List.of("none"));
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        lenient().when(licenseService.getEnvironment(any(), any())).thenReturn(Map.of());
    }

    private static ProgrammingExerciseBuildConfig buildConfigWithFlags(String dockerFlags) {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setDockerFlags(dockerFlags);
        return buildConfig;
    }

    @Test
    void aContainerOverridesTheFieldsItSetsAndInheritsTheRest() {
        var containerFlags = new BuildContainerDockerFlagsDTO("none", Map.of("MODE", "student", "EXTRA", "1"), 1, null, null);

        DockerRunConfig runConfig = service.getDockerRunConfig(buildConfigWithFlags(EXERCISE_FLAGS), exercise, containerFlags);

        assertThat(runConfig).isNotNull();
        assertThat(runConfig.network()).isEqualTo("none");
        assertThat(runConfig.cpuCount()).isEqualTo(1);
        assertThat(runConfig.memory()).as("unset by the container, so the exercise's").isEqualTo(1024);
        assertThat(runConfig.env()).as("merged by name, the container's value winning").containsExactlyInAnyOrder("SHARED=exercise", "MODE=student", "EXTRA=1");
    }

    @Test
    void aContainerWithoutFlagsGetsTheExercisesRunConfig() {
        var buildConfig = buildConfigWithFlags(EXERCISE_FLAGS);

        assertThat(service.getDockerRunConfig(buildConfig, exercise, null)).isEqualTo(service.getDockerRunConfig(buildConfig, exercise));
    }

    @Test
    void containerFlagsApplyAloneWhenTheExerciseSetsNone() {
        var containerFlags = new BuildContainerDockerFlagsDTO("none", null, null, 256, null);

        DockerRunConfig runConfig = service.getDockerRunConfig(buildConfigWithFlags(null), exercise, containerFlags);

        assertThat(runConfig).isNotNull();
        assertThat(runConfig.network()).isEqualTo("none");
        assertThat(runConfig.memory()).isEqualTo(256);
        assertThat(runConfig.env()).isEmpty();
    }

    @Test
    void aContainerNetworkThatIsNotAllowedIsRejected() {
        var containerFlags = new BuildContainerDockerFlagsDTO("host", null, null, null, null);

        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> service.getDockerRunConfig(buildConfigWithFlags(EXERCISE_FLAGS), exercise, containerFlags))
                .withMessageContaining("Invalid network: host");
    }
}
