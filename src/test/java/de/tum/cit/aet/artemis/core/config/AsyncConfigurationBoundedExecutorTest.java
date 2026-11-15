package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class AsyncConfigurationBoundedExecutorTest {

    @ParameterizedTest
    @MethodSource("executors")
    void preservesPoolCapacityAndCallerRunsPolicy(String name, int core, int max, int capacity, String prefix) throws Exception {
        var configuration = new AsyncConfiguration(new TaskExecutionProperties(), new MockEnvironment());
        var executor = (Executor) AsyncConfiguration.class.getMethod(name).invoke(configuration);
        var pool = (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(executor, "executor");
        assertThat(pool).isNotNull();
        assertThat(pool.getCorePoolSize()).isEqualTo(core);
        assertThat(pool.getMaxPoolSize()).isEqualTo(max);
        assertThat(pool.getQueueCapacity()).isEqualTo(capacity);
        assertThat(pool.getThreadNamePrefix()).isEqualTo(prefix);
        ((InitializingBean) executor).afterPropertiesSet();
        try {
            assertThat(pool.getThreadPoolExecutor().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        }
        finally {
            ((DisposableBean) executor).destroy();
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testProfileRunsOnCallingThread(String name, int core, int max, int capacity, String prefix) throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        var configuration = new AsyncConfiguration(new TaskExecutionProperties(), environment);
        var executor = (Executor) AsyncConfiguration.class.getMethod(name).invoke(configuration);
        var executingThread = new AtomicReference<Thread>();
        executor.execute(() -> executingThread.set(Thread.currentThread()));
        assertThat(executingThread.get()).isSameAs(Thread.currentThread());
    }

    private static Stream<Arguments> executors() {
        return Stream.of(Arguments.of("vcsAccessLogExecutor", 1, 1, 1000, "vcs-access-log-"), Arguments.of("submissionVersionExecutor", 4, 8, 500, "submission-version-"),
                Arguments.of("buildTriggerExecutor", 2, 4, 2000, "build-trigger-"));
    }
}
