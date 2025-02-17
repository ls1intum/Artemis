package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LicenseConfigurationTest {

    @Test
    void testMatlabLicenseServer() {
        String licenseServer = "1234@license-server";
        LicenseConfiguration licenseConfiguration = new LicenseConfiguration(new LicenseConfiguration.MatLabLicense(licenseServer));
        assertThat(licenseConfiguration.getMatlabLicenseServer()).isEqualTo(licenseServer);
    }

    @Test
    void testMatlabNullRecord() {
        LicenseConfiguration licenseConfiguration = new LicenseConfiguration(null);
        assertThat(licenseConfiguration.getMatlabLicenseServer()).isNull();
    }

    @Test
    void testMatlabNullValue() {
        LicenseConfiguration licenseConfiguration = new LicenseConfiguration(new LicenseConfiguration.MatLabLicense(null));
        assertThat(licenseConfiguration.getMatlabLicenseServer()).isNull();
    }
}
