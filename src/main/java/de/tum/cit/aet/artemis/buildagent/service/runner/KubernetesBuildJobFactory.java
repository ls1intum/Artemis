package de.tum.cit.aet.artemis.buildagent.service.runner;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.config.KubernetesBuildRunnerProperties;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import io.fabric8.kubernetes.api.model.CapabilitiesBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SeccompProfileBuilder;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraintBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

/**
 * Maps a LocalCI build job to a Kubernetes Job specification.
 */
@Lazy
@Component
@Profile(PROFILE_BUILDAGENT)
@ConditionalOnProperty(prefix = "artemis.continuous-integration", name = "build-runner", havingValue = "kubernetes")
public class KubernetesBuildJobFactory {

    static final String MANAGED_LABEL = "artemis.cit.tum.de/managed";

    static final String BUILD_JOB_LABEL = "artemis.cit.tum.de/build-job";

    static final String BUILD_JOB_ANNOTATION = "artemis.cit.tum.de/build-job-id";

    static final String AGENT_LABEL = "artemis.cit.tum.de/build-agent";

    static final String BUILDER_CONTAINER = "builder";

    static final String HELPER_CONTAINER = "helper";

    static final String WORKSPACE_VOLUME = "workspace";

    static final String WORKSPACE_PATH = "/var/tmp";

    static final String INPUT_READY_FILE = WORKSPACE_PATH + "/artemis-input-ready";

    static final String HELPER_STOP_FILE = WORKSPACE_PATH + "/artemis-helper-stop";

    private final KubernetesBuildRunnerProperties properties;

    @Value("${artemis.continuous-integration.build-timeout-seconds.max:240}")
    private int maximumBuildTimeoutSeconds;

    @Value("${artemis.continuous-integration.proxies.use-system-proxy:false}")
    private boolean useSystemProxy;

    @Value("${artemis.continuous-integration.proxies.default.http-proxy:}")
    private String httpProxy;

    @Value("${artemis.continuous-integration.proxies.default.https-proxy:}")
    private String httpsProxy;

    @Value("${artemis.continuous-integration.proxies.default.no-proxy:}")
    private String noProxy;

    @Value("${artemis.continuous-integration.container-flags-limit.max-cpu-count:0}")
    private int maximumCpuCount;

    @Value("${artemis.continuous-integration.container-flags-limit.max-memory:0}")
    private int maximumMemoryMegabytes;

    public KubernetesBuildJobFactory(KubernetesBuildRunnerProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the Kubernetes Job specification for one LocalCI build attempt.
     *
     * @param buildJob       the queued build and its execution configuration
     * @param jobName        the DNS-compatible name assigned to the Job
     * @param buildAgentName the build agent responsible for the Job lifecycle
     * @return a Job ready to be created in the configured build namespace
     */
    public Job createJob(BuildJobQueueItem buildJob, String jobName, String buildAgentName) {
        DockerRunConfig runConfig = buildJob.buildConfig().dockerRunConfig() != null ? buildJob.buildConfig().dockerRunConfig() : new DockerRunConfig(null, null, 0, 0, 0);
        validateRunConfig(runConfig);

        String networkMode = runConfig.network() == null || runConfig.network().isBlank() ? "default" : runConfig.network();
        Map<String, String> labels = new HashMap<>();
        labels.put("app.kubernetes.io/name", "artemis-localci-build");
        labels.put("app.kubernetes.io/managed-by", "artemis-localci");
        labels.put(MANAGED_LABEL, "true");
        labels.put(BUILD_JOB_LABEL, jobName);
        labels.put(AGENT_LABEL, KubernetesBuildJobRunner.toDnsLabel(buildAgentName));
        labels.put("artemis.cit.tum.de/retry", Integer.toString(buildJob.retryCount()));
        labels.put("artemis.cit.tum.de/network", networkMode);

        long activeDeadline = effectiveBuildTimeout(buildJob) + properties.podStartTimeoutSeconds() + properties.resultCollectionTimeoutSeconds()
                + properties.activeDeadlineGraceSeconds();
        var podSpec = new PodSpecBuilder().withRestartPolicy("Never").withServiceAccountName(properties.workloadServiceAccount()).withAutomountServiceAccountToken(false)
                .withNodeSelector(properties.nodeSelector()).withTolerations(tolerations()).withImagePullSecrets(imagePullSecrets()).addNewVolume().withName(WORKSPACE_VOLUME)
                .withNewEmptyDir().withSizeLimit(new Quantity(properties.workspaceSizeLimit())).endEmptyDir().endVolume()
                .withTopologySpreadConstraints(new TopologySpreadConstraintBuilder().withMaxSkew(1).withTopologyKey("kubernetes.io/hostname")
                        .withWhenUnsatisfiable("ScheduleAnyway").withLabelSelector(new LabelSelectorBuilder().withMatchLabels(Map.of(MANAGED_LABEL, "true")).build()).build())
                .withContainers(builderContainer(buildJob, runConfig), helperContainer()).build();

        if ("none".equals(networkMode)) {
            podSpec.setInitContainers(List.of(networkIsolationContainer()));
        }

        return new JobBuilder().withNewMetadata().withName(jobName).withNamespace(properties.namespace()).withLabels(labels)
                .withAnnotations(Map.of(BUILD_JOB_ANNOTATION, buildJob.id())).endMetadata().withNewSpec().withBackoffLimit(0)
                .withTtlSecondsAfterFinished(properties.jobTtlSeconds()).withActiveDeadlineSeconds(activeDeadline).withNewTemplate().withNewMetadata().withLabels(labels)
                .withAnnotations(Map.of(BUILD_JOB_ANNOTATION, buildJob.id())).endMetadata().withSpec(podSpec).endTemplate().endSpec().build();
    }

    private Container builderContainer(BuildJobQueueItem buildJob, DockerRunConfig runConfig) {
        String command = "set +e\n" + "while [ ! -f " + INPUT_READY_FILE + " ]; do sleep 0.1; done\n" + "cd " + WORKSPACE_PATH + "/testing-dir\n" + "bash " + WORKSPACE_PATH
                + "/script.sh\n" + "code=$?\n" + "date -u +%Y-%m-%dT%H:%M:%SZ > " + WORKSPACE_PATH + "/artemis-finished-at\n" + "printf '%s\\n' \"$code\" > " + WORKSPACE_PATH
                + "/artemis-exit-code\n" + "exit \"$code\"\n";

        return new ContainerBuilder().withName(BUILDER_CONTAINER).withImage(buildJob.buildConfig().dockerImage()).withImagePullPolicy(properties.imagePullPolicy())
                .withCommand("bash", "-c", command).withEnv(environment(runConfig)).withResources(builderResources(runConfig)).withSecurityContext(builderSecurityContext())
                .withVolumeMounts(new VolumeMountBuilder().withName(WORKSPACE_VOLUME).withMountPath(WORKSPACE_PATH).build()).build();
    }

    /**
     * Security context for the container that executes untrusted student code.
     * <p>
     * Kubernetes leaves seccomp unconfined unless a profile is requested, while the Docker build runner always applies the container runtime's default profile. Requesting
     * {@code RuntimeDefault} therefore restores parity with the Docker runner instead of silently running with a weaker sandbox.
     * <p>
     * {@code allowPrivilegeEscalation} and the default capability set are deliberately left untouched: exercise images legitimately install packages as root and use setuid
     * tooling such as {@code sudo} or {@code gosu}. Disabling either would break those exercises, and the Docker runner does not restrict them either. Isolation of the builder
     * relies on the Pod boundary, the disabled service account token, and the optional network isolation init container.
     *
     * @return the security context applied to the builder container
     */
    private SecurityContext builderSecurityContext() {
        return new SecurityContextBuilder().withSeccompProfile(new SeccompProfileBuilder().withType("RuntimeDefault").build()).build();
    }

    private Container helperContainer() {
        String command = "set -eu; while [ ! -f " + HELPER_STOP_FILE + " ]; do sleep 0.2; done";
        return new ContainerBuilder().withName(HELPER_CONTAINER).withImage(properties.helperImage()).withImagePullPolicy(properties.imagePullPolicy())
                .withCommand("sh", "-c", command)
                .withSecurityContext(new SecurityContextBuilder().withRunAsUser(0L).withRunAsNonRoot(false).withAllowPrivilegeEscalation(false)
                        .withCapabilities(new CapabilitiesBuilder().withDrop("ALL").build()).build())
                .withResources(new ResourceRequirementsBuilder().addToRequests("cpu", new Quantity("25m")).addToRequests("memory", new Quantity("32Mi"))
                        .addToLimits("cpu", new Quantity("250m")).addToLimits("memory", new Quantity("256Mi")).build())
                .withVolumeMounts(new VolumeMountBuilder().withName(WORKSPACE_VOLUME).withMountPath(WORKSPACE_PATH).build()).build();
    }

    private Container networkIsolationContainer() {
        // One rule per line with "set -eu" so that a failing rule aborts the init container instead of leaving the build partially isolated.
        String command = """
                set -eu
                iptables -A INPUT -i lo -j ACCEPT
                iptables -A OUTPUT -o lo -j ACCEPT
                iptables -P INPUT DROP
                iptables -P OUTPUT DROP
                ip6tables -A INPUT -i lo -j ACCEPT
                ip6tables -A OUTPUT -o lo -j ACCEPT
                ip6tables -P INPUT DROP
                ip6tables -P OUTPUT DROP
                """;
        return new ContainerBuilder().withName("network-isolation").withImage(properties.helperImage()).withImagePullPolicy(properties.imagePullPolicy())
                .withCommand("sh", "-c", command)
                .withSecurityContext(new SecurityContextBuilder().withRunAsUser(0L).withRunAsNonRoot(false).withAllowPrivilegeEscalation(false)
                        .withCapabilities(new CapabilitiesBuilder().withAdd("NET_ADMIN").withDrop("ALL").build()).build())
                .withResources(new ResourceRequirementsBuilder().addToRequests("cpu", new Quantity("10m")).addToRequests("memory", new Quantity("16Mi"))
                        .addToLimits("cpu", new Quantity("100m")).addToLimits("memory", new Quantity("64Mi")).build())
                .build();
    }

    private ResourceRequirements builderResources(DockerRunConfig runConfig) {
        String defaultCpu = properties.defaultResources() != null ? properties.defaultResources().cpu() : "2";
        String defaultMemory = properties.defaultResources() != null ? properties.defaultResources().memory() : "2Gi";
        String defaultEphemeralStorage = properties.defaultResources() != null ? properties.defaultResources().ephemeralStorage() : "4Gi";
        int requestedCpu = runConfig.cpuCount();
        if (requestedCpu > 0 && maximumCpuCount > 0) {
            requestedCpu = Math.min(requestedCpu, maximumCpuCount);
        }
        int requestedMemory = runConfig.memory();
        if (requestedMemory > 0 && maximumMemoryMegabytes > 0) {
            requestedMemory = Math.min(requestedMemory, maximumMemoryMegabytes);
        }
        String cpu = requestedCpu > 0 ? Integer.toString(requestedCpu) : defaultCpu;
        String memory = requestedMemory > 0 ? requestedMemory + "Mi" : defaultMemory;
        return new ResourceRequirementsBuilder().addToRequests("cpu", new Quantity(cpu)).addToRequests("memory", new Quantity(memory))
                .addToRequests("ephemeral-storage", new Quantity(defaultEphemeralStorage)).addToLimits("cpu", new Quantity(cpu)).addToLimits("memory", new Quantity(memory))
                .addToLimits("ephemeral-storage", new Quantity(defaultEphemeralStorage)).build();
    }

    private List<EnvVar> environment(DockerRunConfig runConfig) {
        List<EnvVar> environment = new ArrayList<>();
        if (useSystemProxy) {
            environment.add(env("HTTP_PROXY", httpProxy));
            environment.add(env("HTTPS_PROXY", httpsProxy));
            environment.add(env("NO_PROXY", noProxy));
        }
        if (runConfig.env() != null) {
            for (String value : runConfig.env()) {
                int separator = value.indexOf('=');
                if (separator < 1) {
                    throw new LocalCIException("Kubernetes build environment variables must use KEY=value syntax: " + value);
                }
                String name = value.substring(0, separator);
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new LocalCIException("Invalid Kubernetes build environment variable name: " + name);
                }
                environment.add(env(name, value.substring(separator + 1)));
            }
        }
        return environment;
    }

    private EnvVar env(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private List<Toleration> tolerations() {
        if (properties.tolerations() == null) {
            return List.of();
        }
        return properties.tolerations().stream().map(value -> new TolerationBuilder().withKey(value.key()).withOperator(value.operator()).withValue(value.value())
                .withEffect(value.effect()).withTolerationSeconds(value.tolerationSeconds()).build()).toList();
    }

    private List<LocalObjectReference> imagePullSecrets() {
        if (properties.imagePullSecrets() == null) {
            return List.of();
        }
        return properties.imagePullSecrets().stream().map(value -> new LocalObjectReferenceBuilder().withName(value).build()).toList();
    }

    /**
     * Caps the timeout requested by a build job with the configured maximum.
     * <p>
     * Shared with {@link KubernetesBuildJobRunner} so that the time the runner waits for an execution and the {@code activeDeadlineSeconds} of the Job stay consistent.
     *
     * @param buildJob the build job whose requested timeout should be capped
     * @return the effective build timeout in seconds
     */
    int effectiveBuildTimeout(BuildJobQueueItem buildJob) {
        int requestedTimeout = buildJob.buildConfig().timeoutSeconds();
        return requestedTimeout > 0 && requestedTimeout < maximumBuildTimeoutSeconds ? requestedTimeout : maximumBuildTimeoutSeconds;
    }

    private void validateRunConfig(DockerRunConfig runConfig) {
        // Docker's default "memory swap equals memory" setting disables swap and maps directly to
        // Kubernetes' memory limit. A distinct swap allowance has no portable Pod equivalent.
        if (runConfig.memorySwap() > 0 && runConfig.memorySwap() != runConfig.memory()) {
            throw new LocalCIException("Custom memory swap is not supported by the Kubernetes build runner");
        }
        if (runConfig.network() != null && !runConfig.network().isBlank() && !"none".equals(runConfig.network())) {
            throw new LocalCIException("Named Docker networks are not supported by the Kubernetes build runner. Supported values are empty and 'none'.");
        }
    }
}
