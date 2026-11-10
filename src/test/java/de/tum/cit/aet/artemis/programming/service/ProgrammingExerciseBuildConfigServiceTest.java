package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.core.config.ContainerDefaults;
import de.tum.cit.aet.artemis.core.config.ContainerDefaultsConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDockerFlagsDTO;

/**
 * Tests how the container settings of an installation, see {@link ContainerDefaults}, combine with those of an exercise,
 * of its language and of the build container a job runs. The order is: the installation, then the language, then the
 * exercise, then the container, and a later one overrides an earlier one by name.
 */
class ProgrammingExerciseBuildConfigServiceTest {

    private static final List<String> ALLOWED_NETWORKS = List.of("none", "artemis-restricted");

    private static final String EXERCISE_FLAGS = "{\"network\":\"\",\"env\":{\"SHARED\":\"exercise\",\"MODE\":\"exercise\"},\"cpuCount\":2,\"memory\":1024,\"memorySwap\":0}";

    private LicenseService licenseService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        licenseService = mock(LicenseService.class);
        when(licenseService.getEnvironment(ProgrammingLanguage.JAVA, null)).thenReturn(Map.of());
        exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
    }

    private ProgrammingExerciseBuildConfigService serviceWith(List<String> env, String network) {
        var service = new ProgrammingExerciseBuildConfigService(licenseService, mock(ProgrammingLanguageConfiguration.class),
                new ContainerDefaults(new ContainerDefaultsConfiguration(env, network), ALLOWED_NETWORKS));
        ReflectionTestUtils.setField(service, "allowedNetworks", ALLOWED_NETWORKS);
        return service;
    }

    private ProgrammingExerciseBuildConfigService serviceWithoutDefaults() {
        return serviceWith(List.of(), null);
    }

    private static ProgrammingExerciseBuildConfig buildConfigWithFlags(String dockerFlagsJson) {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setDockerFlags(dockerFlagsJson);
        return buildConfig;
    }

    @Test
    void withoutDefaultsAndFlagsThereIsNoRunConfigAsBefore() {
        assertThat(serviceWithoutDefaults().getDockerRunConfig(new ProgrammingExerciseBuildConfig(), exercise)).isNull();
    }

    @Test
    void withoutDefaultsTheExerciseAloneDecidesAsBefore() {
        DockerRunConfig config = serviceWithoutDefaults().getDockerRunConfig(buildConfigWithFlags("{\"network\":\"none\",\"env\":{\"A\":\"1\"},\"cpuCount\":2}"), exercise);

        assertThat(config.network()).isEqualTo("none");
        assertThat(config.env()).containsExactly("A=1");
        assertThat(config.cpuCount()).isEqualTo(2);
    }

    @Test
    void theDefaultsApplyToAnExerciseThatSetsNothing() {
        DockerRunConfig config = serviceWith(List.of("CI=true"), "none").getDockerRunConfig(new ProgrammingExerciseBuildConfig(), exercise);

        assertThat(config).isNotNull();
        assertThat(config.env()).containsExactly("CI=true");
        assertThat(config.network()).isEqualTo("none");
        assertThat(config.cpuCount()).isZero();
    }

    @Test
    void anExerciseVariableOverridesADefaultWithTheSameNameAndTheOtherDefaultsStay() {
        DockerRunConfig config = serviceWith(List.of("CI=true", "TOOL=default"), null).getDockerRunConfig(buildConfigWithFlags("{\"env\":{\"TOOL\":\"exercise\",\"EXTRA\":\"x\"}}"),
                exercise);

        assertThat(config.env()).containsExactlyInAnyOrder("CI=true", "TOOL=exercise", "EXTRA=x");
    }

    @Test
    void theNetworkOfTheExerciseReplacesTheDefaultNetwork() {
        DockerRunConfig config = serviceWith(List.of(), "none").getDockerRunConfig(buildConfigWithFlags("{\"network\":\"artemis-restricted\"}"), exercise);

        assertThat(config.network()).isEqualTo("artemis-restricted");
    }

    @Test
    void anExerciseThatSetsOnlyResourcesStillGetsTheDefaultNetworkAndVariables() {
        DockerRunConfig config = serviceWith(List.of("CI=true"), "none").getDockerRunConfig(buildConfigWithFlags("{\"cpuCount\":4,\"memory\":1024}"), exercise);

        assertThat(config.network()).isEqualTo("none");
        assertThat(config.env()).containsExactly("CI=true");
        assertThat(config.cpuCount()).isEqualTo(4);
        assertThat(config.memory()).isEqualTo(1024);
    }

    @Test
    void aLanguageVariableOverridesADefaultAndAnExerciseVariableOverridesBoth() {
        when(licenseService.getEnvironment(ProgrammingLanguage.JAVA, null)).thenReturn(Map.of("TOOL", "language", "LICENSE", "server"));

        DockerRunConfig withoutExerciseVariable = serviceWith(List.of("TOOL=default", "OTHER=default"), null).getDockerRunConfig(new ProgrammingExerciseBuildConfig(), exercise);
        assertThat(withoutExerciseVariable.env()).containsExactlyInAnyOrder("TOOL=language", "LICENSE=server", "OTHER=default");

        DockerRunConfig withExerciseVariable = serviceWith(List.of("TOOL=default"), null).getDockerRunConfig(buildConfigWithFlags("{\"env\":{\"TOOL\":\"exercise\"}}"), exercise);
        assertThat(withExerciseVariable.env()).containsExactlyInAnyOrder("TOOL=exercise", "LICENSE=server");
    }

    @Test
    void theDefaultsAlsoApplyWithoutAnExercise() {
        DockerRunConfig config = serviceWith(List.of("CI=true"), "none").getDockerRunConfig(new ProgrammingExerciseBuildConfig(), null);

        assertThat(config.env()).containsExactly("CI=true");
        assertThat(config.network()).isEqualTo("none");
    }

    @Test
    void aContainerOverridesTheFieldsItSetsAndInheritsTheRest() {
        var containerFlags = new BuildContainerDockerFlagsDTO("none", Map.of("MODE", "student", "EXTRA", "1"), 1, null, null);

        DockerRunConfig runConfig = serviceWithoutDefaults().getDockerRunConfig(buildConfigWithFlags(EXERCISE_FLAGS), exercise, containerFlags);

        assertThat(runConfig).isNotNull();
        assertThat(runConfig.network()).isEqualTo("none");
        assertThat(runConfig.cpuCount()).isEqualTo(1);
        assertThat(runConfig.memory()).as("unset by the container, so the exercise's").isEqualTo(1024);
        assertThat(runConfig.env()).as("merged by name, the container's value winning").containsExactlyInAnyOrder("SHARED=exercise", "MODE=student", "EXTRA=1");
    }

    @Test
    void aContainerWithoutFlagsGetsTheExercisesRunConfig() {
        var service = serviceWithoutDefaults();
        var buildConfig = buildConfigWithFlags(EXERCISE_FLAGS);

        assertThat(service.getDockerRunConfig(buildConfig, exercise, null)).isEqualTo(service.getDockerRunConfig(buildConfig, exercise));
    }

    @Test
    void containerFlagsApplyAloneWhenTheExerciseSetsNone() {
        var containerFlags = new BuildContainerDockerFlagsDTO("none", null, null, 256, null);

        DockerRunConfig runConfig = serviceWithoutDefaults().getDockerRunConfig(buildConfigWithFlags(null), exercise, containerFlags);

        assertThat(runConfig).isNotNull();
        assertThat(runConfig.network()).isEqualTo("none");
        assertThat(runConfig.memory()).isEqualTo(256);
        assertThat(runConfig.env()).isEmpty();
    }

    @Test
    void aContainerNetworkThatIsNotAllowedIsRejected() {
        var containerFlags = new BuildContainerDockerFlagsDTO("host", null, null, null, null);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> serviceWithoutDefaults().getDockerRunConfig(buildConfigWithFlags(EXERCISE_FLAGS), exercise, containerFlags))
                .withMessageContaining("Invalid network: host");
    }

    @Test
    void aContainerOverridesTheDefaultsAndTheExerciseByName() {
        var containerFlags = new BuildContainerDockerFlagsDTO("none", Map.of("MODE", "student"), null, null, null);

        DockerRunConfig runConfig = serviceWith(List.of("CI=true", "MODE=default"), "artemis-restricted").getDockerRunConfig(buildConfigWithFlags(EXERCISE_FLAGS), exercise,
                containerFlags);

        assertThat(runConfig.network()).as("the container's network replaces the default").isEqualTo("none");
        assertThat(runConfig.env()).containsExactlyInAnyOrder("CI=true", "SHARED=exercise", "MODE=student");
    }

    @Test
    void aContainerWithoutANetworkGetsTheDefaultNetwork() {
        var containerFlags = new BuildContainerDockerFlagsDTO(null, null, 1, null, null);

        DockerRunConfig runConfig = serviceWith(List.of(), "none").getDockerRunConfig(buildConfigWithFlags(EXERCISE_FLAGS), exercise, containerFlags);

        assertThat(runConfig.network()).as("neither the exercise nor the container sets one").isEqualTo("none");
        assertThat(runConfig.cpuCount()).isEqualTo(1);
    }
}
