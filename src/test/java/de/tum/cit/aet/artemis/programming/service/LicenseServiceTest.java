package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.config.LicenseConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LicenseServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private LicenseService licenseService;

    @Test
    void testIsLicensedNoneRequired() {
        boolean isLicensed = licenseService.isLicensed(ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE);
        assertThat(isLicensed).isTrue();
    }

    @Test
    void testGetLicenseNoneRequired() {
        Map<String, String> environment = licenseService.getEnvironment(ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE);
        assertThat(environment).isEmpty();
    }

    @Test
    void testIsLicensedMatlab() {
        boolean isLicensed = licenseService.isLicensed(ProgrammingLanguage.MATLAB, null);
        assertThat(isLicensed).isTrue();
    }

    @Test
    void testGetLicenseMatlab() {
        Map<String, String> environment = licenseService.getEnvironment(ProgrammingLanguage.MATLAB, null);
        assertThat(environment).containsEntry("MLM_LICENSE_FILE", "1234@license-server");
    }

    @Test
    void testIsLicensedMatlabUnlicensed() {
        LicenseConfiguration licenseConfiguration = new LicenseConfiguration(null);
        LicenseService licenseService = new LicenseService(licenseConfiguration);
        boolean isLicensed = licenseService.isLicensed(ProgrammingLanguage.MATLAB, null);
        assertThat(isLicensed).isFalse();
    }
}
