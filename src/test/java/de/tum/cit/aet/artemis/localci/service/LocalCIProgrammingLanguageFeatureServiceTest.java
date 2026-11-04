package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.service.LicenseService;

@ExtendWith(MockitoExtension.class)
class LocalCIProgrammingLanguageFeatureServiceTest {

    @Mock
    private LicenseService licenseService;

    private LocalCIProgrammingLanguageFeatureService service;

    @BeforeEach
    void setUp() {
        when(licenseService.isLicensed(any(), any())).thenReturn(true);
        service = new LocalCIProgrammingLanguageFeatureService(licenseService);
    }

    @Test
    void shouldSupportSequentialTestRunsForKotlin() {
        var kotlin = service.getProgrammingLanguageFeatures(ProgrammingLanguage.KOTLIN);

        assertThat(kotlin.sequentialTestRuns()).isTrue();
        // Kotlin has no static code analysis, so a sequential exercise selects the template without the static suffix
        assertThat(kotlin.staticCodeAnalysis()).isFalse();
    }

    @Test
    void shouldSupportStaticCodeAnalysisForSwift() {
        var swift = service.getProgrammingLanguageFeatures(ProgrammingLanguage.SWIFT);

        assertThat(swift.staticCodeAnalysis()).isTrue();
        assertThat(swift.projectTypes()).containsExactly(ProjectType.PLAIN);
        assertThat(StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.SWIFT)).containsExactly(StaticCodeAnalysisTool.SWIFTLINT);
    }

    @ParameterizedTest
    @EnumSource(value = ProgrammingLanguage.class, names = { "C_SHARP", "DART", "GO", "HASKELL", "JAVA", "JAVASCRIPT", "KOTLIN", "PYTHON", "RUBY", "RUST", "SWIFT", "TYPESCRIPT" })
    void shouldSupportSequentialTestRunsWhereATemplateExists(ProgrammingLanguage language) {
        assertThat(service.getProgrammingLanguageFeatures(language).sequentialTestRuns()).isTrue();
    }
}
