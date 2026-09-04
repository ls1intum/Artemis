package de.tum.cit.aet.artemis.buildagent.service.runner;

import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.AGENT_LABEL;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.BUILDER_CONTAINER;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.BUILD_JOB_ANNOTATION;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.HELPER_CONTAINER;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.HELPER_STOP_FILE;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.INPUT_READY_FILE;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.MANAGED_LABEL;
import static de.tum.cit.aet.artemis.buildagent.service.runner.KubernetesBuildJobFactory.WORKSPACE_PATH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.config.KubernetesBuildRunnerProperties;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.service.BuildLogsMap;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.exception.ImagePullException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.LogWatch;

/**
 * Executes LocalCI builds as native Kubernetes Jobs. A trusted helper container transfers the
 * agent-cloned repositories and result archive through the Kubernetes exec API.
 */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
@ConditionalOnProperty(prefix = "artemis.continuous-integration", name = "build-runner", havingValue = "kubernetes")
public class KubernetesBuildJobRunner implements BuildJobRunner {

    private static final Logger log = LoggerFactory.getLogger(KubernetesBuildJobRunner.class);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private static final Duration LOG_DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private static final String INPUT_ARCHIVE_FILE = "/var/tmp/artemis-localci-input.tar";

    /** The directory the build script runs in; result paths are relative to it. Mirrors KubernetesBuildJobFactory. */
    private static final String TESTING_DIRECTORY_PATH = "/var/tmp/testing-dir";

    private static final List<String> TERMINAL_IMAGE_PULL_FAILURES = List.of("ErrImagePull", "ImagePullBackOff", "InvalidImageName");

    private static final List<String> TERMINAL_START_FAILURES = Stream
            .concat(Stream.of("CreateContainerConfigError", "CreateContainerError", "RunContainerError"), TERMINAL_IMAGE_PULL_FAILURES.stream()).toList();

    private final KubernetesClient kubernetesClient;

    private final KubernetesBuildRunnerProperties properties;

    private final KubernetesBuildJobFactory jobFactory;

    private final KubernetesBuildArchiveService archiveService;

    private final BuildLogsMap buildLogsMap;

    private final TempFileUtilService tempFileUtilService;

    private final int maxLogLineBytes;

    private final Map<String, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentName;

    public KubernetesBuildJobRunner(KubernetesClient kubernetesClient, KubernetesBuildRunnerProperties properties, KubernetesBuildJobFactory jobFactory,
            KubernetesBuildArchiveService archiveService, BuildLogsMap buildLogsMap, TempFileUtilService tempFileUtilService,
            @Value("${artemis.continuous-integration.build-logs.max-chars-per-line:1024}") int maxLogLineBytes) {
        this.kubernetesClient = kubernetesClient;
        this.properties = properties;
        this.jobFactory = jobFactory;
        this.archiveService = archiveService;
        this.buildLogsMap = buildLogsMap;
        this.tempFileUtilService = tempFileUtilService;
        this.maxLogLineBytes = Math.max(1, maxLogLineBytes);
    }

    @Override
    public BuildRunnerType type() {
        return BuildRunnerType.KUBERNETES;
    }

    @Override
    public BuildRunnerStatus status() {
        try {
            return BuildRunnerStatus.available(kubernetesClient.getKubernetesVersion().getGitVersion());
        }
        catch (Exception e) {
            return BuildRunnerStatus.unavailable(e.getMessage());
        }
    }

    @Override
    public BuildJobRunnerResult execute(BuildJobQueueItem buildJob, PreparedBuildJob preparedBuildJob) {
        String jobName = jobName(buildJob, buildAgentName);
        ActiveExecution execution = new ActiveExecution(jobName);
        ActiveExecution previous = activeExecutions.putIfAbsent(buildJob.id(), execution);
        if (previous != null) {
            throw new LocalCIException("Build job " + buildJob.id() + " already has an active Kubernetes execution");
        }

        Path inputArchive = null;
        Path resultArchive = null;
        boolean resultHandedOff = false;
        try {
            deleteJobIfPresent(jobName);
            kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).resource(jobFactory.createJob(buildJob, jobName, buildAgentName)).create();
            append(buildJob.id(), "Created Kubernetes Job " + properties.namespace() + "/" + jobName);

            // Everything up to a running Pod is scheduling and image pulling, bounded by podStartTimeoutSeconds rather
            // than by the exercise's build timeout. Marking it keeps that phase out of the build budget, the way the
            // Docker runner keeps its image pull out of it.
            execution.startingPod = true;
            Pod pod;
            try {
                pod = waitForPod(jobName, properties.podStartTimeoutSeconds());
            }
            finally {
                execution.startingPod = false;
            }
            execution.podName = pod.getMetadata().getName();
            append(buildJob.id(), "Kubernetes build Pod " + execution.podName + " is running");

            BuildLogOutputStream buildLogOutput = new BuildLogOutputStream(buildJob.id(), buildLogsMap, maxLogLineBytes);
            execution.buildLogOutput = buildLogOutput;
            execution.logWatch = kubernetesClient.pods().inNamespace(properties.namespace()).withName(execution.podName).inContainer(BUILDER_CONTAINER).watchLog(buildLogOutput);

            inputArchive = archiveService.createInputArchive(buildJob, preparedBuildJob);
            uploadInputArchive(execution.podName, inputArchive);
            append(buildJob.id(), "Transferred repositories and build script to Kubernetes Job " + jobName);

            ContainerStateTerminated termination = waitForBuilder(execution.podName, effectiveExecutionWait(buildJob));
            drainLogWatch(execution);
            int exitCode = termination.getExitCode() != null ? termination.getExitCode() : -1;
            ZonedDateTime completedAt = parseCompletionDate(termination.getFinishedAt());
            if ("OOMKilled".equals(termination.getReason())) {
                throw new LocalCIException("Kubernetes build workload exceeded its memory limit (OOMKilled)");
            }
            append(buildJob.id(), "Kubernetes build script finished with exit code " + exitCode);

            resultArchive = collectResultArchive(execution.podName, buildJob);
            signalHelperStop(execution.podName);
            InputStream resultStream = new DeleteOnCloseInputStream(Files.newInputStream(resultArchive), resultArchive);
            resultHandedOff = true;
            return new BuildJobRunnerResult(resultStream, exitCode, completedAt, () -> deleteExecution(buildJob.id(), execution));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalCIException("Kubernetes build execution was interrupted", e);
        }
        catch (KubernetesClientException e) {
            throw new LocalCIException("Kubernetes API operation failed for build job " + buildJob.id() + ": " + e.getMessage(), e);
        }
        catch (IOException e) {
            throw new LocalCIException("Could not transfer Kubernetes build files for build job " + buildJob.id(), e);
        }
        finally {
            if (inputArchive != null) {
                try {
                    Files.deleteIfExists(inputArchive);
                }
                catch (IOException e) {
                    log.warn("Could not delete temporary Kubernetes input archive {}", inputArchive, e);
                }
            }
            if (!resultHandedOff && resultArchive != null) {
                try {
                    Files.deleteIfExists(resultArchive);
                }
                catch (IOException e) {
                    log.warn("Could not delete temporary Kubernetes result archive {}", resultArchive, e);
                }
            }
            if (!resultHandedOff) {
                deleteExecution(buildJob.id(), execution);
            }
        }
    }

    private Pod waitForPod(String jobName, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        String latestReason = "waiting for scheduling";
        while (System.nanoTime() < deadline) {
            checkInterrupted();
            List<Pod> pods = kubernetesClient.pods().inNamespace(properties.namespace()).withLabel("job-name", jobName).list().getItems();
            if (!pods.isEmpty()) {
                Pod pod = pods.getFirst();
                String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : null;
                latestReason = podDiagnostic(pod);
                failForTerminalContainerWaitingReason(pod);
                if ("Running".equals(phase) && containersStarted(pod)) {
                    return pod;
                }
                if ("Failed".equals(phase)) {
                    throw new LocalCIException("Kubernetes build Pod failed before startup: " + latestReason);
                }
            }
            Thread.sleep(POLL_INTERVAL);
        }
        throw new LocalCIException("Timed out after " + timeoutSeconds + " seconds waiting for Kubernetes build Pod to start: " + latestReason);
    }

    private ContainerStateTerminated waitForBuilder(String podName, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            checkInterrupted();
            Pod pod = kubernetesClient.pods().inNamespace(properties.namespace()).withName(podName).get();
            if (pod == null) {
                throw new LocalCIException("Kubernetes build Pod " + podName + " disappeared during execution");
            }
            ContainerStatus builder = containerStatus(pod, BUILDER_CONTAINER);
            if (builder != null && builder.getState() != null && builder.getState().getTerminated() != null) {
                return builder.getState().getTerminated();
            }
            failForTerminalContainerWaitingReason(pod);
            if (pod.getStatus() != null && "Failed".equals(pod.getStatus().getPhase())) {
                throw new LocalCIException("Kubernetes build Pod failed: " + podDiagnostic(pod));
            }
            Thread.sleep(POLL_INTERVAL);
        }
        throw new LocalCIException("Timed out while waiting for the Kubernetes build workload to finish");
    }

    private void uploadInputArchive(String podName, Path archive) throws IOException {
        try (InputStream input = Files.newInputStream(archive)) {
            boolean uploaded = kubernetesClient.pods().inNamespace(properties.namespace()).withName(podName).inContainer(HELPER_CONTAINER).file(INPUT_ARCHIVE_FILE).upload(input);
            if (!uploaded) {
                throw new LocalCIException("Kubernetes helper could not upload the build input archive");
            }
        }

        ByteArrayOutputStream error = new ByteArrayOutputStream();
        String command = "set -eu; tar --no-same-owner -xpf " + INPUT_ARCHIVE_FILE + " -C " + WORKSPACE_PATH + "; rm -f " + INPUT_ARCHIVE_FILE + "; chmod -R a+rwX "
                + WORKSPACE_PATH + "; chmod +x " + WORKSPACE_PATH + "/script.sh; touch " + INPUT_READY_FILE;
        int exitCode = exec(podName, OutputStream.nullOutputStream(), error, Duration.ofSeconds(properties.resultCollectionTimeoutSeconds()), "sh", "-c", command);
        if (exitCode != 0) {
            throw new LocalCIException("Kubernetes helper could not unpack the build input archive: " + error.toString(StandardCharsets.UTF_8));
        }
    }

    private Path collectResultArchive(String podName, BuildJobQueueItem buildJob) throws IOException {
        Path archive = tempFileUtilService.createTempFile("artemis-localci-results-" + toDnsLabel(buildJob.id()) + "-", ".tar");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        String command = resultCollectionCommand(buildJob.buildConfig().resultPaths());
        try {
            try (OutputStream output = Files.newOutputStream(archive)) {
                int exitCode = exec(podName, output, error, Duration.ofSeconds(properties.resultCollectionTimeoutSeconds()), "bash", "-c", command);
                if (exitCode != 0) {
                    throw new LocalCIException("Kubernetes helper could not collect build results: " + error.toString(StandardCharsets.UTF_8));
                }
            }
            return archive;
        }
        catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(archive);
            }
            catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    static String resultCollectionCommand(List<String> resultPaths) {
        // Expand the globs from the directory the build script ran in, not from the helper's own working directory.
        // KubernetesBuildJobFactory starts the builder with `cd /var/tmp/testing-dir`, so the standard result paths
        // ("test-reports/*.xml", "**/target/surefire-reports/*.xml", ...) are written relative to that directory.
        // Expanding them one level up matched nothing, and because a miss is not an error the helper still exited 0 with
        // an empty archive: every build reported no test feedback rather than failing visibly. This also matches the
        // Docker runner, whose mv runs in the build working directory.
        StringBuilder command = new StringBuilder("set -eu; shopt -s globstar nullglob; rm -rf /var/tmp/results; mkdir -p /var/tmp/results; cd ").append(TESTING_DIRECTORY_PATH)
                .append(";");
        for (String resultPath : resultPaths) {
            validateResultPath(resultPath);
            // `continue` rather than `|| true`: nullglob already makes a non-matching pattern iterate zero times, so the
            // only thing `|| true` still hid was a genuine mv failure. Overlapping patterns stay safe because each glob
            // is expanded when its own loop starts, by which point an earlier loop has already moved those files away.
            command.append(" for source in ").append(resultPath).append("; do [ -e \"$source\" ] || continue; mv -- \"$source\" /var/tmp/results/; done;");
        }
        command.append(" tar -cpf - -C /var/tmp results");
        return command.toString();
    }

    private static void validateResultPath(String path) {
        if (path == null || path.contains("..") || !path.matches("[a-zA-Z0-9_*./-]+")) {
            throw new LocalCIException("Invalid result path for Kubernetes build execution: " + path);
        }
    }

    private void signalHelperStop(String podName) {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try {
            int exitCode = exec(podName, OutputStream.nullOutputStream(), error, Duration.ofSeconds(10), "touch", HELPER_STOP_FILE);
            if (exitCode != 0) {
                log.warn("Could not stop Kubernetes helper in Pod {}: {}", podName, error.toString(StandardCharsets.UTF_8));
            }
        }
        catch (RuntimeException e) {
            // This runs after the result archive has been collected. The Job is deleted afterwards anyway, so a failed stop signal must not discard a successful build.
            log.warn("Could not signal the Kubernetes helper in Pod {} to stop", podName, e);
        }
    }

    private int exec(String podName, OutputStream output, OutputStream error, Duration timeout, String... command) {
        try (ExecWatch watch = kubernetesClient.pods().inNamespace(properties.namespace()).withName(podName).inContainer(HELPER_CONTAINER).writingOutput(output).writingError(error)
                .exec(command)) {
            return watch.exitCode().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            throw new LocalCIException("Timed out while communicating with Kubernetes helper in Pod " + podName, e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalCIException("Interrupted while communicating with Kubernetes helper in Pod " + podName, e);
        }
        catch (Exception e) {
            throw new LocalCIException("Could not execute Kubernetes helper command in Pod " + podName, e);
        }
    }

    private Duration effectiveExecutionWait(BuildJobQueueItem buildJob) {
        // Reuse the capping of the Job factory so that this wait never outlives the activeDeadlineSeconds of the Job it waits for.
        return Duration.ofSeconds(jobFactory.effectiveBuildTimeout(buildJob) + properties.activeDeadlineGraceSeconds());
    }

    private boolean containersStarted(Pod pod) {
        ContainerStatus builder = containerStatus(pod, BUILDER_CONTAINER);
        ContainerStatus helper = containerStatus(pod, HELPER_CONTAINER);
        return builder != null && helper != null && Boolean.TRUE.equals(builder.getStarted()) && Boolean.TRUE.equals(helper.getStarted());
    }

    private ContainerStatus containerStatus(Pod pod, String name) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return null;
        }
        return pod.getStatus().getContainerStatuses().stream().filter(status -> name.equals(status.getName())).findFirst().orElse(null);
    }

    private void failForTerminalContainerWaitingReason(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return;
        }
        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            if (status.getState() != null && status.getState().getWaiting() != null && TERMINAL_START_FAILURES.contains(status.getState().getWaiting().getReason())) {
                String reason = status.getState().getWaiting().getReason();
                String message = "Kubernetes container " + status.getName() + " could not start: " + reason + " - " + status.getState().getWaiting().getMessage();
                // A missing or misconfigured exercise image is a problem of the exercise, not of this agent. Reporting it as a typed image-pull failure keeps it out of the
                // consecutive failure counter that pauses the agent, exactly like the Docker runner does. The helper image belongs to the agent, so it stays an agent failure.
                if (BUILDER_CONTAINER.equals(status.getName()) && TERMINAL_IMAGE_PULL_FAILURES.contains(reason)) {
                    throw new ImagePullException(message);
                }
                throw new LocalCIException(message);
            }
        }
    }

    private String podDiagnostic(Pod pod) {
        if (pod.getStatus() == null) {
            return "Pod status is unavailable";
        }
        StringBuilder diagnostic = new StringBuilder();
        if (pod.getStatus().getReason() != null) {
            diagnostic.append(pod.getStatus().getReason());
        }
        if (pod.getStatus().getMessage() != null) {
            diagnostic.append(" ").append(pod.getStatus().getMessage());
        }
        if (pod.getStatus().getConditions() != null) {
            for (PodCondition condition : pod.getStatus().getConditions()) {
                if ("False".equals(condition.getStatus()) && condition.getReason() != null) {
                    diagnostic.append(" ").append(condition.getReason());
                    if (condition.getMessage() != null) {
                        diagnostic.append(": ").append(condition.getMessage());
                    }
                }
            }
        }
        return diagnostic.isEmpty() ? "phase " + pod.getStatus().getPhase() : diagnostic.toString().trim();
    }

    @Override
    public void cancel(String buildJobId) {
        ActiveExecution execution = activeExecutions.remove(buildJobId);
        if (execution != null) {
            closeLogWatch(execution);
            deleteJob(execution.jobName);
        }
        else {
            jobsForBuildJob(buildJobId).forEach(this::deleteJob);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * True while the Pod is being scheduled and its images pulled. Kubernetes bounds that phase with
     * {@code podStartTimeoutSeconds}, so counting it against the exercise's build timeout would report a build as
     * {@code TIMEOUT} before its script ever ran, on nothing worse than a cold image on a fresh node.
     */
    @Override
    public boolean isFetchingImage(String buildJobId) {
        ActiveExecution execution = activeExecutions.get(buildJobId);
        return execution != null && execution.startingPod;
    }

    @Override
    public boolean isActive(String buildJobId) {
        if (activeExecutions.containsKey(buildJobId)) {
            return true;
        }
        return !jobsForBuildJob(buildJobId).isEmpty();
    }

    /**
     * Reconciles Jobs left by an interrupted instance of this build agent.
     */
    @Override
    @Scheduled(fixedDelayString = "${artemis.continuous-integration.kubernetes.orphan-cleanup-interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void cleanupOrphans() {
        String agentLabel = toDnsLabel(buildAgentName);
        var jobs = kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).withLabel(MANAGED_LABEL, "true").withLabel(AGENT_LABEL, agentLabel).list().getItems();
        var activeJobNames = activeExecutions.values().stream().map(execution -> execution.jobName).collect(Collectors.toSet());
        jobs.stream().map(job -> job.getMetadata().getName()).filter(name -> !activeJobNames.contains(name)).forEach(this::deleteJob);
    }

    private List<String> jobsForBuildJob(String buildJobId) {
        String agentLabel = toDnsLabel(buildAgentName);
        return kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).withLabel(MANAGED_LABEL, "true").withLabel(AGENT_LABEL, agentLabel).list().getItems()
                .stream().filter(job -> belongsToBuildAgentExecution(job, buildJobId, agentLabel)).map(job -> job.getMetadata().getName()).toList();
    }

    static boolean belongsToBuildAgentExecution(Job job, String buildJobId, String agentLabel) {
        return job.getMetadata() != null && job.getMetadata().getLabels() != null && agentLabel.equals(job.getMetadata().getLabels().get(AGENT_LABEL))
                && job.getMetadata().getAnnotations() != null && buildJobId.equals(job.getMetadata().getAnnotations().get(BUILD_JOB_ANNOTATION));
    }

    private void deleteExecution(String buildJobId, ActiveExecution execution) {
        activeExecutions.remove(buildJobId, execution);
        closeLogWatch(execution);
        deleteJob(execution.jobName);
    }

    private void deleteJobIfPresent(String jobName) throws InterruptedException {
        if (kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).withName(jobName).get() == null) {
            return;
        }
        deleteJob(jobName);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline && kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).withName(jobName).get() != null) {
            Thread.sleep(POLL_INTERVAL);
        }
        if (kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).withName(jobName).get() != null) {
            throw new LocalCIException("Previous Kubernetes Job " + jobName + " could not be deleted");
        }
    }

    private void deleteJob(String jobName) {
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(properties.namespace()).withName(jobName).withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
        }
        catch (Exception e) {
            log.warn("Could not delete Kubernetes Job {}/{}: {}", properties.namespace(), jobName, e.getMessage());
        }
    }

    private void closeLogWatch(ActiveExecution execution) {
        if (execution.logWatch != null) {
            execution.logWatch.close();
            execution.logWatch = null;
        }
        if (execution.buildLogOutput != null) {
            execution.buildLogOutput.flushLine();
        }
    }

    private void drainLogWatch(ActiveExecution execution) {
        if (execution.logWatch != null) {
            try {
                execution.logWatch.onClose().toCompletableFuture().get(LOG_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                log.debug("Kubernetes build log stream did not close cleanly before the drain timeout: {}", e.getMessage());
            }
        }
        closeLogWatch(execution);
    }

    private ZonedDateTime parseCompletionDate(String finishedAt) {
        try {
            return finishedAt != null ? ZonedDateTime.parse(finishedAt) : ZonedDateTime.now();
        }
        catch (Exception ignored) {
            return ZonedDateTime.now();
        }
    }

    private void append(String buildJobId, String message) {
        buildLogsMap.appendBuildLogEntry(buildJobId, message);
        log.info(message);
    }

    private void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Build thread interrupted");
        }
    }

    static String jobName(BuildJobQueueItem buildJob, String buildAgentName) {
        ZonedDateTime buildStartDate = buildJob.jobTimingInfo() != null ? buildJob.jobTimingInfo().buildStartDate() : null;
        String claimIdentity = buildJob.id() + '|' + buildJob.retryCount() + '|' + buildAgentName + '|' + buildStartDate;
        String suffix = "-r" + buildJob.retryCount() + "-" + sha256(claimIdentity).substring(0, 10);
        String base = "local-ci-" + toDnsLabel(buildJob.id());
        if (base.length() + suffix.length() <= 63) {
            return base + suffix;
        }
        String hash = sha256(buildJob.id()).substring(0, 10);
        int prefixLength = 63 - suffix.length() - hash.length() - 1;
        return stripNonAlphanumericEdges(base.substring(0, Math.max(1, prefixLength))) + "-" + hash + suffix;
    }

    static String toDnsLabel(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        normalized = stripNonAlphanumericEdges(normalized);
        if (normalized.isBlank()) {
            return "unknown";
        }
        if (normalized.length() <= 63) {
            return normalized;
        }
        return stripNonAlphanumericEdges(normalized.substring(0, 52)) + "-" + sha256(value).substring(0, 10);
    }

    private static String stripNonAlphanumericEdges(String value) {
        return value.replaceAll("^[^a-z0-9]+", "").replaceAll("[^a-z0-9]+$", "");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static final class BuildLogOutputStream extends OutputStream {

        private final String buildJobId;

        private final BuildLogsMap buildLogsMap;

        private final int maxLogLineBytes;

        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        BuildLogOutputStream(String buildJobId, BuildLogsMap buildLogsMap, int maxLogLineBytes) {
            this.buildJobId = buildJobId;
            this.buildLogsMap = buildLogsMap;
            this.maxLogLineBytes = maxLogLineBytes;
        }

        @Override
        public synchronized void write(int value) {
            if (value == '\n') {
                flushLine();
            }
            else if (value != '\r') {
                if (line.size() < maxLogLineBytes) {
                    line.write(value);
                }
            }
        }

        synchronized void flushLine() {
            if (line.size() == 0) {
                return;
            }
            buildLogsMap.appendBuildLogEntry(buildJobId, line.toString(StandardCharsets.UTF_8));
            line.reset();
        }
    }

    private final class ActiveExecution {

        private final String jobName;

        /** True while the Pod is being scheduled and its images pulled, which the build budget does not pay for. */
        private volatile boolean startingPod;

        private volatile String podName;

        private volatile LogWatch logWatch;

        private volatile BuildLogOutputStream buildLogOutput;

        private ActiveExecution(String jobName) {
            this.jobName = jobName;
        }
    }

    private static final class DeleteOnCloseInputStream extends FilterInputStream {

        private final Path path;

        private DeleteOnCloseInputStream(InputStream input, Path path) {
            super(input);
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            }
            finally {
                Files.deleteIfExists(path);
            }
        }
    }
}
