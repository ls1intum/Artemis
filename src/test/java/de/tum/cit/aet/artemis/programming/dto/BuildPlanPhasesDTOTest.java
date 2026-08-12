package de.tum.cit.aet.artemis.programming.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;

class BuildPlanPhasesDTOTest {

    private static final String DOCKER_IMAGE = "ghcr.io/ls1intum/artemis-maven-template:latest";

    private static BuildPhaseDTO phase(String name) {
        return new BuildPhaseDTO(name, "echo " + name, BuildPhaseCondition.ALWAYS, false, List.of());
    }

    @Test
    void testNormalizesLegacyConfigurationIntoSingleContainer() throws Exception {
        var legacyJson = new BuildPlanPhasesDTO(List.of(phase("compile"), phase("test")), DOCKER_IMAGE).toBuildPlanConfiguration();

        var containers = BuildPlanPhasesDTO.fromBuildPlanConfiguration(legacyJson).effectiveContainers();

        assertThat(containers).singleElement().satisfies(container -> {
            assertThat(container.name()).isEqualTo(BuildContainerDTO.DEFAULT_CONTAINER_NAME);
            assertThat(container.dockerImage()).isEqualTo(DOCKER_IMAGE);
            assertThat(container.phases()).extracting(BuildPhaseDTO::name).containsExactly("compile", "test");
            // a legacy build plan checks out the repositories configured on the exercise, so the container scopes none
            assertThat(container.repositories()).isNull();
        });
    }

    @Test
    void testKeepsScopedRepositoriesOfContainers() throws Exception {
        var studentTests = new BuildContainerDTO("student_tests", DOCKER_IMAGE, List.of(new BuildContainerRepositoryDTO(RepositoryType.USER)), List.of(phase("test")));
        var instructorTests = new BuildContainerDTO("instructor_tests", DOCKER_IMAGE, List.of(new BuildContainerRepositoryDTO(RepositoryType.USER),
                new BuildContainerRepositoryDTO(RepositoryType.TESTS), new BuildContainerRepositoryDTO(RepositoryType.AUXILIARY, "grading-utils")), List.of(phase("test")));
        var json = new BuildPlanPhasesDTO(null, null, List.of(studentTests, instructorTests)).toBuildPlanConfiguration();

        var containers = BuildPlanPhasesDTO.fromBuildPlanConfiguration(json).effectiveContainers();

        // the student tests container never receives the test repository, which is what keeps the instructor tests out of it
        assertThat(containers.getFirst().repositories()).extracting(BuildContainerRepositoryDTO::type).containsExactly(RepositoryType.USER);
        assertThat(containers.getLast().repositories()).extracting(BuildContainerRepositoryDTO::type).containsExactly(RepositoryType.USER, RepositoryType.TESTS,
                RepositoryType.AUXILIARY);
        assertThat(containers.getLast().repositories()).extracting(BuildContainerRepositoryDTO::name).containsExactly(null, null, "grading-utils");
    }

    @Test
    void testKeepsContainersOfMultiContainerConfiguration() throws Exception {
        var tests = new BuildContainerDTO("student_tests", DOCKER_IMAGE, List.of(phase("test")));
        var lint = new BuildContainerDTO("lint", "ghcr.io/example/lint:latest", List.of(phase("checkstyle")));
        var json = new BuildPlanPhasesDTO(null, null, List.of(tests, lint)).toBuildPlanConfiguration();

        var containers = BuildPlanPhasesDTO.fromBuildPlanConfiguration(json).effectiveContainers();

        assertThat(containers).extracting(BuildContainerDTO::name).containsExactly("student_tests", "lint");
        assertThat(containers).extracting(BuildContainerDTO::dockerImage).containsExactly(DOCKER_IMAGE, "ghcr.io/example/lint:latest");
    }

    @Test
    void testAllPhasesFlattensPhasesOfEveryContainer() throws Exception {
        var tests = new BuildContainerDTO("student_tests", DOCKER_IMAGE, List.of(phase("compile"), phase("test")));
        var lint = new BuildContainerDTO("lint", "ghcr.io/example/lint:latest", List.of(phase("checkstyle")));
        var json = new BuildPlanPhasesDTO(null, null, List.of(tests, lint)).toBuildPlanConfiguration();

        var phases = BuildPlanPhasesDTO.fromBuildPlanConfiguration(json).allPhases();

        assertThat(phases).extracting(BuildPhaseDTO::name).containsExactly("compile", "test", "checkstyle");
    }

    @Test
    void testAllPhasesReturnsPhasesOfLegacyConfiguration() throws Exception {
        var json = new BuildPlanPhasesDTO(List.of(phase("compile"), phase("test")), DOCKER_IMAGE).toBuildPlanConfiguration();

        assertThat(BuildPlanPhasesDTO.fromBuildPlanConfiguration(json).allPhases()).extracting(BuildPhaseDTO::name).containsExactly("compile", "test");
    }

    @Test
    void testAllPhasesIsEmptyForBlankConfiguration() throws Exception {
        assertThat(BuildPlanPhasesDTO.fromBuildPlanConfiguration(null).allPhases()).isEmpty();
    }

    @Test
    void testReturnsNoContainersForBlankConfiguration() throws Exception {
        assertThat(BuildPlanPhasesDTO.fromBuildPlanConfiguration(null).effectiveContainers()).isEmpty();
        assertThat(BuildPlanPhasesDTO.fromBuildPlanConfiguration("  ").effectiveContainers()).isEmpty();
        assertThat(BuildPlanPhasesDTO.fromBuildPlanConfiguration("{}").effectiveContainers()).isEmpty();
    }

    @Test
    void testContainersTakePrecedenceOverLegacyPhases() {
        var container = new BuildContainerDTO("lint", "ghcr.io/example/lint:latest", List.of(phase("checkstyle")));
        var mixed = new BuildPlanPhasesDTO(List.of(phase("compile")), DOCKER_IMAGE, List.of(container));

        assertThat(mixed.effectiveContainers()).containsExactly(container);
    }

    @Test
    void testLegacyConfigurationSerializationOmitsContainers() throws Exception {
        var json = new BuildPlanPhasesDTO(List.of(phase("compile")), DOCKER_IMAGE).toBuildPlanConfiguration();

        assertThat(json).doesNotContain("containers");
    }
}
