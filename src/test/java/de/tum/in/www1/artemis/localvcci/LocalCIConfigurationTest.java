package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;

class LocalCIConfigurationTest {

    @Test
    void testBytes() {
        assertThat("512 Bytes").isEqualTo(LocalCIConfiguration.formatMemory(512));
    }

    @Test
    void testKilobytes() {
        assertThat("1 KB").isEqualTo(LocalCIConfiguration.formatMemory(1024));
        assertThat("999 KB").isEqualTo(LocalCIConfiguration.formatMemory(1024 * 999));
    }

    @Test
    void testMegabytes() {
        assertThat("1 MB").isEqualTo(LocalCIConfiguration.formatMemory(1024 * 1024));
        assertThat("1023 MB").isEqualTo(LocalCIConfiguration.formatMemory(1024 * 1024 * 1023L));
    }

    @Test
    void testGigabytes() {
        assertThat("1.0 GB").isEqualTo(LocalCIConfiguration.formatMemory(1024 * 1024 * 1024L));
        assertThat("1.5 GB").isEqualTo(LocalCIConfiguration.formatMemory(1024 * 1024 * 1024 * 3L / 2));
    }
}
