package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import de.tum.cit.aet.artemis.core.config.TaskSchedulingConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class WorkerSchedulerProfileTest {

    @Test
    void importsSharedSchedulerWithoutScanningCoreConfiguration() {
        workerContext().withPropertyValues("artemis.aiworker.id=profile-test", "artemis.aiworker.image=sha256:" + "a".repeat(64), "artemis.aiworker.workload=document-check",
                "artemis.aiworker.profile=plain-text", "spring.threads.virtual.enabled=true").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TaskScheduler.class);
                    assertThat(context.getBean(TaskScheduler.class)).isInstanceOf(ThreadPoolTaskScheduler.class);
                    var scheduler = (ThreadPoolTaskScheduler) context.getBean(TaskScheduler.class);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
                });
    }

    @Test
    void explicitWorkerProfileRejectsMissingWorkerSettings() {
        workerContext().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("Invalid workload capability");
        });
    }

    private ApplicationContextRunner workerContext() {
        return new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class)).withUserConfiguration(WorkerMessaging.class)
                .withBean(DistributedDataProvider.class, LocalDataProviderService::new);
    }

    @ParameterizedTest
    @MethodSource("schedulerProfiles")
    void registersOneSharedSchedulerOnlyForCoreOrWorker(String profiles, boolean bootVirtualThreads) {
        var active = profiles.isEmpty() ? new String[0] : profiles.split(",");
        boolean schedulerExpected = List.of(active).contains("core") || List.of(active).contains("aiworker");
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(active))
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(TaskSchedulingConfiguration.class).withPropertyValues("spring.threads.virtual.enabled=" + bootVirtualThreads).run(context -> {
                    assertThat(context).hasNotFailed();
                    if (!schedulerExpected) {
                        assertThat(context).doesNotHaveBean(TaskSchedulingConfiguration.class).doesNotHaveBean(TaskScheduler.class);
                        return;
                    }
                    assertThat(context).hasSingleBean(TaskScheduler.class);
                    assertThat(context.getBean(TaskScheduler.class)).isSameAs(context.getBean("taskScheduler")).isInstanceOf(ThreadPoolTaskScheduler.class);
                    var scheduler = (ThreadPoolTaskScheduler) context.getBean(TaskScheduler.class);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
                    assertThat(scheduler.submit(() -> Thread.currentThread().isVirtual()).get(5, TimeUnit.SECONDS)).isTrue();
                });
    }

    private static Stream<Arguments> schedulerProfiles() {
        return Stream.of("", "prod", "core", "prod,core", "aiworker", "prod,aiworker", "core,aiworker", "buildagent", "localci,localvc")
                .flatMap(profiles -> Stream.of(false, true).map(virtualThreads -> Arguments.of(profiles, virtualThreads)));
    }
}
