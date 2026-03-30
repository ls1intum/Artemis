package de.tum.cit.aet.artemis.programming.service.localci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

@ExtendWith(MockitoExtension.class)
class BuildPhaseEvaluationServiceTest {

    @Mock
    private ExerciseDateService exerciseDateService;

    @Mock
    private ProgrammingExerciseParticipation participation;

    @InjectMocks
    private BuildPhaseEvaluationService buildPhaseEvaluationService;

    @Test
    void testAllAlwaysPhases_allActive() {
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.ALWAYS, false, List.of("target/surefire-reports/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(2);
        assertThat(activePhases).containsExactly(compile, test);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactly("target/surefire-reports/*.xml");
    }

    @Test
    void testAfterDueDatePhases_beforeDueDate_onlyAlwaysActive() {
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/surefire-reports/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(1);
        assertThat(activePhases).containsExactly(compile);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).isEmpty();
    }

    @Test
    void testAfterDueDatePhases_afterDueDate_allActive() {
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/surefire-reports/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(true);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(2);
        assertThat(activePhases).containsExactly(compile, test);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactly("target/surefire-reports/*.xml");
    }

    @Test
    void testNoPhases_emptyResult() {
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(Collections.emptyList(), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).isEmpty();
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).isEmpty();
    }

    @Test
    void testMultipleAfterDueDatePhases_beforeDueDate_noneActive() {
        BuildPhaseDTO test1 = new BuildPhaseDTO("test1", "mvn test -pl module1", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("module1/target/*.xml"));
        BuildPhaseDTO test2 = new BuildPhaseDTO("test2", "mvn test -pl module2", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("module2/target/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(test1, test2), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).isEmpty();
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).isEmpty();
    }

    @Test
    void testMultipleAfterDueDatePhases_afterDueDate_allActive() {
        BuildPhaseDTO test1 = new BuildPhaseDTO("test1", "mvn test -pl module1", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("module1/target/*.xml"));
        BuildPhaseDTO test2 = new BuildPhaseDTO("test2", "mvn test -pl module2", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("module2/target/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(test1, test2), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(true);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(2);
        assertThat(activePhases).containsExactly(test1, test2);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactlyInAnyOrder("module1/target/*.xml", "module2/target/*.xml");
    }

    @Test
    void testResultPathsAggregatedFromMultiplePhases() {
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, List.of("compile-results/*.xml"));
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.ALWAYS, false, List.of("test-results/*.xml", "integration/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(2);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactlyInAnyOrder("compile-results/*.xml", "test-results/*.xml", "integration/*.xml");
    }

    @Test
    void testPhaseWithNullResultPaths_noTestsExpected() {
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(1);
        assertThat(activePhases).containsExactly(compile);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).isEmpty();
    }

    @Test
    void testPhaseWithEmptyResultPaths_noTestsExpected() {
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, Collections.emptyList());
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile), "ubuntu:latest");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        assertThat(activePhases).hasSize(1);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).isEmpty();
    }

    @Test
    void testTypicalSetup_compileAlways_testAfterDueDate_beforeDueDate() {
        // Typical configuration: compile phase always runs, test phase only after due date
        BuildPhaseDTO compile = new BuildPhaseDTO("Compile", "mvn clean compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("Test", "mvn test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/surefire-reports/*.xml"));
        BuildPhaseDTO sca = new BuildPhaseDTO("Static Analysis", "mvn checkstyle:checkstyle", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/checkstyle-result.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test, sca), "maven:3.9-eclipse-temurin-21");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(false);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        // Before due date: only compile runs, no result paths
        assertThat(activePhases).hasSize(1);
        assertThat(activePhases).containsExactly(compile);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).isEmpty();
    }

    @Test
    void testTypicalSetup_compileAlways_testAfterDueDate_afterDueDate() {
        // Same configuration, but after due date
        BuildPhaseDTO compile = new BuildPhaseDTO("Compile", "mvn clean compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("Test", "mvn test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/surefire-reports/*.xml"));
        BuildPhaseDTO sca = new BuildPhaseDTO("Static Analysis", "mvn checkstyle:checkstyle", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/checkstyle-result.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test, sca), "maven:3.9-eclipse-temurin-21");

        when(exerciseDateService.isAfterDueDate(participation)).thenReturn(true);

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        // After due date: all phases run, result paths present
        assertThat(activePhases).hasSize(3);
        assertThat(activePhases).containsExactly(compile, test, sca);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactlyInAnyOrder("target/surefire-reports/*.xml", "target/checkstyle-result.xml");
    }

    @Test
    void testTemplateParticipation_beforeDueDate_allPhasesActive() {
        // Template participations should always run all phases so instructors get full feedback
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        BuildPhaseDTO compile = new BuildPhaseDTO("Compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("Test", "mvn test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/surefire-reports/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test), "ubuntu:latest");

        // ExerciseDateService should not even be consulted for template participations
        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, templateParticipation);

        assertThat(activePhases).hasSize(2);
        assertThat(activePhases).containsExactly(compile, test);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactly("target/surefire-reports/*.xml");
    }

    @Test
    void testSolutionParticipation_beforeDueDate_allPhasesActive() {
        // Solution participations should always run all phases so instructors get full feedback
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        BuildPhaseDTO compile = new BuildPhaseDTO("Compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
        BuildPhaseDTO test = new BuildPhaseDTO("Test", "mvn test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/surefire-reports/*.xml"));
        BuildPhaseDTO sca = new BuildPhaseDTO("SCA", "mvn checkstyle:checkstyle", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("target/checkstyle-result.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(compile, test, sca), "ubuntu:latest");

        // ExerciseDateService should not even be consulted for solution participations
        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, solutionParticipation);

        assertThat(activePhases).hasSize(3);
        assertThat(activePhases).containsExactly(compile, test, sca);
        assertThat(BuildPhaseEvaluationService.gatherResultPaths(activePhases)).containsExactlyInAnyOrder("target/surefire-reports/*.xml", "target/checkstyle-result.xml");
    }

    @Test
    void testTemplateParticipation_allAfterDueDatePhases_allActive() {
        // Even if all phases are AFTER_DUE_DATE, template participation should still run them all
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        BuildPhaseDTO test1 = new BuildPhaseDTO("test1", "mvn test -pl module1", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("module1/*.xml"));
        BuildPhaseDTO test2 = new BuildPhaseDTO("test2", "mvn test -pl module2", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("module2/*.xml"));
        BuildPlanPhasesDTO phases = new BuildPlanPhasesDTO(List.of(test1, test2), "ubuntu:latest");

        List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, templateParticipation);

        assertThat(activePhases).hasSize(2);
        assertThat(activePhases).containsExactly(test1, test2);
    }
}
