package de.tum.cit.aet.artemis.buildagent.service.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.config.KubernetesBuildRunnerProperties;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;

class KubernetesBuildJobFactoryTest {

    private KubernetesBuildJobFactory factory;

    @BeforeEach
    void setUp() {
        var properties = new KubernetesBuildRunnerProperties("builds", "helper:test", "IfNotPresent", "workload", 20, 30, 60, 15, 30, "3Gi",
                new KubernetesBuildRunnerProperties.Resources("500m", "512Mi", "2Gi"), Map.of("pool", "build"),
                List.of(new KubernetesBuildRunnerProperties.Toleration("build", "Equal", "true", "NoSchedule", null)), List.of("registry-secret"));
        factory = new KubernetesBuildJobFactory(properties);
        ReflectionTestUtils.setField(factory, "maximumBuildTimeoutSeconds", 240);
        ReflectionTestUtils.setField(factory, "maximumCpuCount", 4);
        ReflectionTestUtils.setField(factory, "maximumMemoryMegabytes", 4096);
        ReflectionTestUtils.setField(factory, "useSystemProxy", true);
        ReflectionTestUtils.setField(factory, "httpProxy", "http://proxy.example");
        ReflectionTestUtils.setField(factory, "httpsProxy", "https://proxy.example");
        ReflectionTestUtils.setField(factory, "noProxy", ".cluster.local");
    }

    @Test
    void createsJobWithRunnerIsolationAndMappedFlags() {
        var runConfig = new DockerRunConfig(List.of("COURSE_ID=42", "EMPTY="), null, 8, 8192, 8192);

        Job job = factory.createJob(buildJob(runConfig), "local-ci-job-r2", "Agent One");

        assertThat(job.getMetadata().getNamespace()).isEqualTo("builds");
        assertThat(job.getMetadata().getLabels()).containsEntry(KubernetesBuildJobFactory.MANAGED_LABEL, "true").containsEntry(KubernetesBuildJobFactory.AGENT_LABEL, "agent-one")
                .containsEntry("artemis.cit.tum.de/retry", "2");
        assertThat(job.getMetadata().getAnnotations()).containsEntry(KubernetesBuildJobFactory.BUILD_JOB_ANNOTATION, "job/id");
        assertThat(job.getSpec().getBackoffLimit()).isZero();
        assertThat(job.getSpec().getActiveDeadlineSeconds()).isEqualTo(165L);

        var podSpec = job.getSpec().getTemplate().getSpec();
        assertThat(podSpec.getServiceAccountName()).isEqualTo("workload");
        assertThat(podSpec.getAutomountServiceAccountToken()).isFalse();
        assertThat(podSpec.getNodeSelector()).containsEntry("pool", "build");
        assertThat(podSpec.getImagePullSecrets()).extracting(reference -> reference.getName()).containsExactly("registry-secret");
        assertThat(podSpec.getTolerations()).singleElement().satisfies(toleration -> {
            assertThat(toleration.getKey()).isEqualTo("build");
            assertThat(toleration.getEffect()).isEqualTo("NoSchedule");
        });
        assertThat(podSpec.getInitContainers()).isNullOrEmpty();

        Container builder = container(job, KubernetesBuildJobFactory.BUILDER_CONTAINER);
        assertThat(builder.getImage()).isEqualTo("ubuntu:24.04");
        assertThat(builder.getEnv()).extracting(env -> env.getName()).contains("HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "COURSE_ID", "EMPTY");
        assertThat(builder.getEnv()).filteredOn(env -> "COURSE_ID".equals(env.getName())).singleElement().extracting(env -> env.getValue()).isEqualTo("42");
        assertThat(builder.getResources().getLimits()).containsEntry("cpu", new Quantity("4")).containsEntry("memory", new Quantity("4096Mi")).containsEntry("ephemeral-storage",
                new Quantity("2Gi"));

        Container helper = container(job, KubernetesBuildJobFactory.HELPER_CONTAINER);
        assertThat(helper.getImage()).isEqualTo("helper:test");
        assertThat(helper.getSecurityContext().getCapabilities().getDrop()).containsExactly("ALL");
        assertThat(helper.getSecurityContext().getAllowPrivilegeEscalation()).isFalse();
    }

    @Test
    void networkNoneAddsTrustedIsolationInitContainer() {
        Job job = factory.createJob(buildJob(new DockerRunConfig(List.of(), "none", 0, 0, 0)), "local-ci-job-r2", "agent");

        assertThat(job.getSpec().getTemplate().getSpec().getInitContainers()).singleElement().satisfies(container -> {
            assertThat(container.getName()).isEqualTo("network-isolation");
            assertThat(container.getSecurityContext().getCapabilities().getAdd()).contains("NET_ADMIN");
        });
        assertThat(job.getMetadata().getLabels()).containsEntry("artemis.cit.tum.de/network", "none");
    }

    @Test
    void rejectsUnsupportedRunFlagsAndMalformedEnvironment() {
        assertThatThrownBy(() -> factory.createJob(buildJob(new DockerRunConfig(List.of(), "course-network", 0, 0, 0)), "job", "agent")).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("Named Docker networks");
        assertThatThrownBy(() -> factory.createJob(buildJob(new DockerRunConfig(List.of(), null, 0, 0, 1)), "job", "agent")).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("memory swap");
        assertThatThrownBy(() -> factory.createJob(buildJob(new DockerRunConfig(List.of("MISSING_VALUE"), null, 0, 0, 0)), "job", "agent")).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("KEY=value");
    }

    @Test
    void createsStableDnsNames() {
        assertThat(KubernetesBuildJobRunner.toDnsLabel("Agent One/@Main")).isEqualTo("agent-one-main");
        assertThat(KubernetesBuildJobRunner.jobName(buildJob(new DockerRunConfig(List.of(), null, 0, 0, 0)), "agent-one")).startsWith("local-ci-").contains("-r2-")
                .hasSizeLessThanOrEqualTo(63);
    }

    private static Container container(Job job, String name) {
        return job.getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> name.equals(container.getName())).findFirst().orElseThrow();
    }

    private static BuildJobQueueItem buildJob(DockerRunConfig runConfig) {
        var repositoryInfo = new RepositoryInfo("repository", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", null, new String[0], new String[0]);
        var timing = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var buildConfig = new BuildConfig("echo test", "ubuntu:24.04", "commit", "assignment-commit", "test-commit", "main", ProgrammingLanguage.C, null, false, false,
                List.of("/var/tmp/testing-dir/results/*.xml"), 100, "assignment", "tests", "solution", runConfig);
        return new BuildJobQueueItem("job/id", "job", new BuildAgentDTO(null, null, null), 1, 2, 3, 2, 1, null, repositoryInfo, timing, buildConfig, null);
    }
}
