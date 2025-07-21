package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;

class BuildAgentConfigurationTest {

    private BuildAgentConfiguration buildAgentConfiguration;

    private ThreadPoolExecutor mockExecutor;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = spy(new BuildAgentConfiguration(new ProgrammingLanguageConfiguration()));
        mockExecutor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        ReflectionTestUtils.setField(buildAgentConfiguration, "buildExecutor", mockExecutor);
    }

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

    @Test
    void testAdjustConcurrentBuildSize_increaseSize() {
        // Initially set to 2
        assertThat(mockExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(mockExecutor.getMaximumPoolSize()).isEqualTo(2);

        // Increase to 4
        boolean result = buildAgentConfiguration.adjustConcurrentBuildSize(4);

        assertThat(result).isTrue();
        assertThat(mockExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(mockExecutor.getMaximumPoolSize()).isEqualTo(4);
        assertThat(buildAgentConfiguration.getThreadPoolSize()).isEqualTo(4);
    }

    @Test
    void testAdjustConcurrentBuildSize_decreaseSize() {
        // Initially set to 4
        mockExecutor.setMaximumPoolSize(4);
        mockExecutor.setCorePoolSize(4);
        ReflectionTestUtils.setField(buildAgentConfiguration, "threadPoolSize", new AtomicInteger(4));

        // Decrease to 2
        boolean result = buildAgentConfiguration.adjustConcurrentBuildSize(2);

        assertThat(result).isTrue();
        assertThat(mockExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(mockExecutor.getMaximumPoolSize()).isEqualTo(2);
        assertThat(buildAgentConfiguration.getThreadPoolSize()).isEqualTo(2);
    }

    @Test
    void testAdjustConcurrentBuildSize_sameSize() {
        // Set to 2 initially
        ReflectionTestUtils.setField(buildAgentConfiguration, "threadPoolSize", new AtomicInteger(2));

        // Set to same size
        boolean result = buildAgentConfiguration.adjustConcurrentBuildSize(2);

        assertThat(result).isTrue();
        assertThat(mockExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(mockExecutor.getMaximumPoolSize()).isEqualTo(2);
        assertThat(buildAgentConfiguration.getThreadPoolSize()).isEqualTo(2);
    }

    @Test
    void testAdjustConcurrentBuildSize_invalidSize() {
        // Test with invalid size (0)
        boolean result = buildAgentConfiguration.adjustConcurrentBuildSize(0);

        assertThat(result).isFalse();
        // Pool size should remain unchanged
        assertThat(mockExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(mockExecutor.getMaximumPoolSize()).isEqualTo(2);
    }

    @Test
    void testAdjustConcurrentBuildSize_negativeSize() {
        // Test with negative size
        boolean result = buildAgentConfiguration.adjustConcurrentBuildSize(-1);

        assertThat(result).isFalse();
        // Pool size should remain unchanged
        assertThat(mockExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(mockExecutor.getMaximumPoolSize()).isEqualTo(2);
    }

    @Test
    void testAdjustConcurrentBuildSize_nullExecutor() {
        // Set executor to null
        ReflectionTestUtils.setField(buildAgentConfiguration, "buildExecutor", null);

        boolean result = buildAgentConfiguration.adjustConcurrentBuildSize(4);

        assertThat(result).isFalse();
    }
}
