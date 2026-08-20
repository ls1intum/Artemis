package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.programming.domain.ProjectType.GRADLE_GRADLE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_BLACKBOX;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_MAVEN;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_GRADLE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_MAVEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.service.LicenseService;

@ExtendWith(MockitoExtension.class)
class HadesProgrammingLanguageFeatureServiceTest {

    @Mock
    private LicenseService licenseService;

    private HadesProgrammingLanguageFeatureService service;

    @BeforeEach
    void setUp() {
        // Keep every language/project type licensed so the base filter does not remove them.
        when(licenseService.isLicensed(any(), any())).thenReturn(true);
        service = new HadesProgrammingLanguageFeatureService(licenseService);
    }

    @Test
    void java_supportsMavenAndGradleButNotBlackbox() {
        // Blackbox is excluded because the Hades result parser cannot ingest its customFeedbacks/*.json output yet
        // (Hades-Scheduler/hades-artemis-result-parser#6).
        var java = service.getProgrammingLanguageFeatures(ProgrammingLanguage.JAVA);
        assertThat(java.projectTypes()).contains(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN).doesNotContain(MAVEN_BLACKBOX);
    }

    @Test
    void blackboxIsNotAdvertisedForAnyLanguage() {
        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            try {
                var feature = service.getProgrammingLanguageFeatures(language);
                assertThat(feature.projectTypes()).as("%s must not advertise MAVEN_BLACKBOX on Hades", language).doesNotContain(MAVEN_BLACKBOX);
            }
            catch (IllegalArgumentException ignored) {
                // Not every language is supported on Hades; only assert for the ones that are.
            }
        }
    }
}
