package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

class BuildPlanConfigurationValidatorTest {

    private static final String DOCKER_IMAGE = "ghcr.io/ls1intum/artemis-maven-template:latest";

    private static BuildPhaseDTO phase(String name) {
        return new BuildPhaseDTO(name, "echo " + name, BuildPhaseCondition.ALWAYS, false, List.of());
    }

    private static BuildPlanPhasesDTO planOf(BuildContainerDTO... containers) {
        return new BuildPlanPhasesDTO(null, null, List.of(containers));
    }

    private static String errorKeyOf(BuildPlanPhasesDTO buildPlan) {
        try {
            BuildPlanConfigurationValidator.validate(buildPlan);
        }
        catch (BadRequestAlertException exception) {
            return exception.getErrorKey();
        }
        throw new AssertionError("Expected the build plan to be rejected, but it was accepted");
    }

    @Test
    void testAcceptsValidMultiContainerBuildPlan() {
        var plan = planOf(new BuildContainerDTO("student_tests", DOCKER_IMAGE, List.of(phase("compile"), phase("test"))),
                new BuildContainerDTO("instructor_tests", DOCKER_IMAGE, List.of(phase("compile"))));

        assertThatCode(() -> BuildPlanConfigurationValidator.validate(plan)).doesNotThrowAnyException();
    }

    @Test
    void testAcceptsLegacyBuildPlan() {
        var legacyPlan = new BuildPlanPhasesDTO(List.of(phase("compile")), DOCKER_IMAGE);

        assertThatCode(() -> BuildPlanConfigurationValidator.validate(legacyPlan)).doesNotThrowAnyException();
    }

    @Test
    void testRejectsBuildPlanWithoutContainers() {
        assertThat(errorKeyOf(new BuildPlanPhasesDTO(null, null, List.of()))).isEqualTo("emptyBuildPlan");
        assertThat(errorKeyOf(new BuildPlanPhasesDTO(List.of(), DOCKER_IMAGE))).isEqualTo("emptyBuildPlan");
    }

    @Test
    void testRejectsContainerWithoutPhases() {
        assertThat(errorKeyOf(planOf(new BuildContainerDTO("tests", DOCKER_IMAGE, List.of())))).isEqualTo("emptyBuildContainer");
    }

    @Test
    void testRejectsDuplicateContainerNamesCaseInsensitively() {
        var plan = planOf(new BuildContainerDTO("tests", DOCKER_IMAGE, List.of(phase("compile"))), new BuildContainerDTO("Tests", DOCKER_IMAGE, List.of(phase("compile"))));

        assertThat(errorKeyOf(plan)).isEqualTo("duplicateBuildContainerName");
    }

    @Test
    void testRejectsInvalidContainerName() {
        assertThat(errorKeyOf(planOf(new BuildContainerDTO("student tests", DOCKER_IMAGE, List.of(phase("compile")))))).isEqualTo("invalidBuildContainerName");
        assertThat(errorKeyOf(planOf(new BuildContainerDTO("1tests", DOCKER_IMAGE, List.of(phase("compile")))))).isEqualTo("invalidBuildContainerName");
    }

    @Test
    void testRejectsInvalidPhaseName() {
        assertThat(errorKeyOf(planOf(new BuildContainerDTO("tests", DOCKER_IMAGE, List.of(phase("invalid phase")))))).isEqualTo("invalidBuildPhaseName");
    }

    @Test
    void testRejectsReservedPhaseName() {
        assertThat(errorKeyOf(planOf(new BuildContainerDTO("tests", DOCKER_IMAGE, List.of(phase("main")))))).isEqualTo("reservedBuildPhaseName");
    }

    @Test
    void testRejectsDuplicatePhaseNamesWithinAContainer() {
        var plan = planOf(new BuildContainerDTO("tests", DOCKER_IMAGE, List.of(phase("compile"), phase("Compile"))));

        assertThat(errorKeyOf(plan)).isEqualTo("duplicateBuildPhaseName");
    }

    @Test
    void testAcceptsBlankPhaseScript() {
        // a blank script is legal: the phase simply does nothing, and rejecting it would also hit the full exercise update
        // and the file import, neither of which can fix the offending phase. The client parser defaults it back to ''.
        for (String blankScript : new String[] { null, "", "   " }) {
            var plan = planOf(new BuildContainerDTO("tests", DOCKER_IMAGE, List.of(new BuildPhaseDTO("compile", blankScript, BuildPhaseCondition.ALWAYS, false, List.of()))));

            assertThatCode(() -> BuildPlanConfigurationValidator.validate(plan)).doesNotThrowAnyException();
        }
    }

    @Test
    void testAcceptsSamePhaseNameInDifferentContainers() {
        // containers execute independently, so a phase name only has to be unique within its container
        var plan = planOf(new BuildContainerDTO("student_tests", DOCKER_IMAGE, List.of(phase("compile"))),
                new BuildContainerDTO("instructor_tests", DOCKER_IMAGE, List.of(phase("compile"))));

        assertThatCode(() -> BuildPlanConfigurationValidator.validate(plan)).doesNotThrowAnyException();
    }

    @Test
    void testNamesTheOffendingContainerAndPhase() {
        var plan = planOf(new BuildContainerDTO("student_tests", DOCKER_IMAGE, List.of(phase("compile"))),
                new BuildContainerDTO("instructor_tests", DOCKER_IMAGE, List.of(phase("test"), phase("test"))));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> BuildPlanConfigurationValidator.validate(plan)).satisfies(exception -> {
            var properties = Objects.requireNonNull(exception.getBody().getProperties());
            // the client resolves the alert text from "message" and interpolates "params" into it, so both are required
            assertThat(properties).containsEntry("message", "error.duplicateBuildPhaseName");
            assertThat(properties.get("params")).isEqualTo(Map.of("container", "instructor_tests", "phase", "test"));
        });
    }
}
