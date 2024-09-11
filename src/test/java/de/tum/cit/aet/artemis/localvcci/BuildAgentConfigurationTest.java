package de.tum.cit.aet.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.config.icl.BuildAgentConfiguration;

class BuildAgentConfigurationTest {

    @Test
    void testBytes() {
        assertThat("512 Bytes").isEqualTo(BuildAgentConfiguration.formatMemory(512));
    }

    @Test
    void testKilobytes() {
        assertThat("1 KB").isEqualTo(BuildAgentConfiguration.formatMemory(1024));
        assertThat("999 KB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 999));
    }

    @Test
    void testMegabytes() {
        assertThat("1 MB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024));
        assertThat("1023 MB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1023L));
    }

    @Test
    void testGigabytes() {
        assertThat("1.0 GB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1024L));
        assertThat("1.5 GB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1024 * 3L / 2));
    }
}
