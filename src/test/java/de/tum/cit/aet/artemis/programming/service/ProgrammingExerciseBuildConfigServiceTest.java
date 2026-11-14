package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.core.config.ContainerDefaults;
import de.tum.cit.aet.artemis.core.config.ContainerDefaultsConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Tests how the container settings of an installation, see {@link ContainerDefaults}, combine with those of an exercise
 * and of its language. The order is: the installation, then the language, then the exercise, and a later one overrides an
 * earlier one by name.
 */
class ProgrammingExerciseBuildConfigServiceTest {

    private static final List<String> ALLOWED_NETWORKS = List.of("none", "artemis-restricted");

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
        var service = new ProgrammingExerciseBuildConfigService(licenseService, new ContainerDefaults(new ContainerDefaultsConfiguration(env, network), ALLOWED_NETWORKS));
        ReflectionTestUtils.setField(service, "allowedNetworks", ALLOWED_NETWORKS);
        return service;
    }

    private static ProgrammingExerciseBuildConfig buildConfigWithFlags(String dockerFlagsJson) {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setDockerFlags(dockerFlagsJson);
        return buildConfig;
    }

    @Test
    void withoutDefaultsAndFlagsThereIsNoRunConfigAsBefore() {
        assertThat(serviceWith(List.of(), null).getDockerRunConfig(new ProgrammingExerciseBuildConfig(), exercise)).isNull();
    }

    @Test
    void theRawSocketOptionOfTheExerciseReachesTheRunConfigAndIsOffOtherwise() {
        DockerRunConfig enabled = serviceWith(List.of(), null).getDockerRunConfig(buildConfigWithFlags("{\"allowRawSockets\":true}"), exercise);
        DockerRunConfig disabled = serviceWith(List.of(), null).getDockerRunConfig(buildConfigWithFlags("{\"cpuCount\":2}"), exercise);

        assertThat(enabled).isNotNull();
        assertThat(enabled.allowRawSockets()).isTrue();
        assertThat(disabled.allowRawSockets()).isFalse();
    }

    @Test
    void withoutDefaultsTheExerciseAloneDecidesAsBefore() {
        DockerRunConfig config = serviceWith(List.of(), null).getDockerRunConfig(buildConfigWithFlags("{\"network\":\"none\",\"env\":{\"A\":\"1\"},\"cpuCount\":2}"), exercise);

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
}
