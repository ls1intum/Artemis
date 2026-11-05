package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

/**
 * Selects the shipped build phase templates for the features that LocalCI offers for Kotlin and Swift.
 * The real templates are read from the classpath, so that a missing or renamed template fails here and not when an instructor creates an exercise.
 */
@ExtendWith(MockitoExtension.class)
class BuildPhasesTemplateServiceDefaultTemplatesTest {

    @Mock
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Mock
    private ResourceLoaderService resourceLoaderService;

    private BuildPhasesTemplateService service;

    @BeforeEach
    void setUp() {
        when(resourceLoaderService.getResource(any(Path.class))).thenAnswer(invocation -> new ClassPathResource(invocation.<Path>getArgument(0).toString()));
        service = new BuildPhasesTemplateService(programmingLanguageConfiguration, resourceLoaderService, new BuildScriptProviderService());
    }

    private static List<String> names(List<BuildPhaseDTO> phases) {
        return phases.stream().map(BuildPhaseDTO::name).toList();
    }

    @Test
    void shouldSelectTheSequentialTemplateForKotlin() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.KOTLIN, Optional.empty(), false, true);

        assertThat(names(phases)).containsExactly("compile_structural", "compile_behavior", "structural", "behavior");
        // the stage directories are the ones that the test repository of a sequential exercise contains
        assertThat(phases.get(2).script()).contains("cd \"structural\"");
        assertThat(phases.get(3).script()).contains("cd \"behavior\"");
        assertThat(phases.get(2).resultPaths()).isNotEmpty();
        assertThat(phases.get(3).resultPaths()).isNotEmpty();
    }

    @Test
    void shouldSelectTheRegularTemplateForKotlinWithoutSequentialTestRuns() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.KOTLIN, Optional.empty(), false, false);

        assertThat(names(phases)).containsExactly("compile", "test");
    }

    @Test
    void shouldFallBackToPlainMavenForJavaSequentialTestRunsWithoutProjectType() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.JAVA, Optional.empty(), false, true);

        assertThat(names(phases)).containsExactly("compile_structural", "compile_behavior", "structural", "behavior");
        assertThat(phases).isEqualTo(service.getBuildPlanPhasesFor(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN), false, true));
    }

    @Test
    void shouldRunStructuralBeforeBehaviorTestsForGo() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.GO, Optional.empty(), false, true);

        assertThat(names(phases)).containsExactly("build", "structural_tests", "behavior_tests");
    }

    @Test
    void shouldRunStructuralBeforeBehaviorTestsForCSharp() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.C_SHARP, Optional.empty(), false, true);

        assertThat(names(phases)).containsExactly("build", "structural_tests", "behavior_tests");
    }

    @Test
    void shouldSelectTheStaticCodeAnalysisTemplateForSwift() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.SWIFT, Optional.of(ProjectType.PLAIN), true, false);

        assertThat(names(phases)).containsExactly("setup", "compile", "test", "finalize", "static_code_analysis");
        var staticCodeAnalysis = phases.getLast();
        assertThat(staticCodeAnalysis.forceRun()).isTrue();
        assertThat(staticCodeAnalysis.script()).contains("swiftlint lint").contains("--reporter checkstyle");
        assertThat(staticCodeAnalysis.resultPaths()).containsExactly("target/swiftlint-result.xml");
        // the build agent only collects a file as static code analysis report if a tool recognizes its name
        assertThat(StaticCodeAnalysisTool.getToolByFilePattern("swiftlint-result.xml")).contains(StaticCodeAnalysisTool.SWIFTLINT);
    }

    @Test
    void shouldNotCopyTheSourcesOfTheTestRepositoryForSwift() throws Exception {
        // the test repository has no Sources directory, and a failing copy would fail every build, because the script stops at the first error
        for (boolean staticAnalysis : List.of(false, true)) {
            var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.SWIFT, Optional.of(ProjectType.PLAIN), staticAnalysis, false);

            assertThat(phases.getFirst().script()).doesNotContain("cp -R Sources");
        }
    }

    @Test
    void shouldSelectNoStaticCodeAnalysisPhaseForSwiftWithoutIt() throws Exception {
        var phases = service.getBuildPlanPhasesFor(ProgrammingLanguage.SWIFT, Optional.of(ProjectType.PLAIN), false, false);

        assertThat(names(phases)).containsExactly("setup", "compile", "test", "finalize");
    }
}
