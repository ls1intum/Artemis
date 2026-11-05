package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.service.LicenseService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;

/**
 * Walks every configuration that the LocalCI feature matrix lets an instructor select and checks that the shipped build phase template exists.
 * The real templates are read from the classpath, so that a feature flag without a template (or a renamed template) fails here and not when an
 * instructor creates an exercise.
 */
@ExtendWith(MockitoExtension.class)
class LocalCILanguageFeatureMatrixTest {

    private record Configuration(ProgrammingLanguage language, Optional<ProjectType> projectType, boolean staticAnalysis, boolean sequential) {
    }

    @Mock
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Mock
    private ResourceLoaderService resourceLoaderService;

    private BuildPhasesTemplateService service;

    @BeforeEach
    void setUp() {
        lenient().when(resourceLoaderService.getResource(any(Path.class))).thenAnswer(invocation -> new ClassPathResource(invocation.<Path>getArgument(0).toString()));
        service = new BuildPhasesTemplateService(programmingLanguageConfiguration, resourceLoaderService, new BuildScriptProviderService());
    }

    private static Set<ProgrammingLanguage> supportedLanguages() {
        // SQL, PowerShell, Ada and PHP are declared in the enum, but not enabled, as they have no templates
        return ProgrammingLanguage.getEnabledLanguages();
    }

    @Test
    void shouldOfferAFeatureEntryForEveryEnabledLanguage() {
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isLicensed(any(), any())).thenReturn(true);
        LocalCIProgrammingLanguageFeatureService featureService = new LocalCIProgrammingLanguageFeatureService(licenseService);

        for (ProgrammingLanguage language : supportedLanguages()) {
            assertThat(featureService.getProgrammingLanguageFeatures(language).programmingLanguage()).isEqualTo(language);
        }
    }

    /**
     * All combinations of the matrix that the server accepts, see {@code ProgrammingExercise#validateStaticCodeAnalysisSettings}.
     */
    private static Stream<Configuration> supportedConfigurations() {
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isLicensed(any(), any())).thenReturn(true);
        LocalCIProgrammingLanguageFeatureService featureService = new LocalCIProgrammingLanguageFeatureService(licenseService);

        List<Configuration> configurations = new ArrayList<>();
        for (ProgrammingLanguage language : supportedLanguages()) {
            ProgrammingLanguageFeature feature = featureService.getProgrammingLanguageFeatures(language);
            List<Optional<ProjectType>> projectTypes = feature.projectTypes().isEmpty() ? List.of(Optional.empty()) : feature.projectTypes().stream().map(Optional::of).toList();
            for (Optional<ProjectType> projectType : projectTypes) {
                boolean fact = projectType.filter(ProjectType.FACT::equals).isPresent();
                boolean blackbox = projectType.filter(ProjectType.MAVEN_BLACKBOX::equals).isPresent();
                boolean staticAnalysisPossible = feature.staticCodeAnalysis() && !fact;
                boolean sequentialPossible = feature.sequentialTestRuns() && !blackbox;

                configurations.add(new Configuration(language, projectType, false, false));
                if (staticAnalysisPossible) {
                    configurations.add(new Configuration(language, projectType, true, false));
                }
                if (sequentialPossible) {
                    configurations.add(new Configuration(language, projectType, false, true));
                }
            }
        }
        return configurations.stream();
    }

    @ParameterizedTest
    @MethodSource("supportedConfigurations")
    void shouldShipABuildPhaseTemplateForEverySupportedConfiguration(Configuration configuration) throws Exception {
        var phases = service.getBuildPlanPhasesFor(configuration.language(), configuration.projectType(), configuration.staticAnalysis(), configuration.sequential());

        assertThat(phases).as("build phases of %s", configuration).isNotNull().isNotEmpty();
        assertThat(phases).as("every phase of %s runs something", configuration).allSatisfy(phase -> assertThat(phase.script()).isNotBlank());
        assertThat(phases).as("%s produces test results", configuration).anyMatch(phase -> phase.resultPaths() != null && !phase.resultPaths().isEmpty());
    }

    private static Stream<Configuration> staticAnalysisConfigurations() {
        return supportedConfigurations().filter(Configuration::staticAnalysis);
    }

    @ParameterizedTest
    @MethodSource("staticAnalysisConfigurations")
    void shouldCollectEveryReportOfTheStaticCodeAnalysisToolsOfTheLanguage(Configuration configuration) throws Exception {
        var phases = service.getBuildPlanPhasesFor(configuration.language(), configuration.projectType(), true, false);
        var reportedTools = phases.stream().filter(phase -> phase.resultPaths() != null).flatMap(phase -> phase.resultPaths().stream())
                .map(resultPath -> StaticCodeAnalysisTool.getToolByFilePattern(Path.of(resultPath).getFileName().toString())).flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StaticCodeAnalysisTool.class)));

        // the build agent only collects a file as static code analysis report if a tool recognizes its name
        // the black-box template only runs Checkstyle
        boolean blackbox = configuration.projectType().filter(ProjectType.MAVEN_BLACKBOX::equals).isPresent();
        Set<StaticCodeAnalysisTool> expectedTools = blackbox ? Set.of(StaticCodeAnalysisTool.CHECKSTYLE)
                : Set.copyOf(StaticCodeAnalysisTool.getToolsForProgrammingLanguage(configuration.language()));
        assertThat(reportedTools).as("reports collected for %s", configuration).containsExactlyInAnyOrderElementsOf(expectedTools);
    }
}
