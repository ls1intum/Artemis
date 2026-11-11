package de.tum.cit.aet.artemis.buildagent.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.HostConfig;

/**
 * Checks from inside a container, started like a build container, that the services of the installation are not reachable from there.
 * <p>
 * Student code runs in build containers. The services that hold the state of the installation, such as the queue store, the cluster port and the database, belong on a network that
 * build containers cannot reach. Whether that holds depends on the network and the firewall of the host, so it cannot be seen from the configuration of Artemis. This probe
 * starts a short-lived container on each network that build containers can use and tries to connect to every configured service.
 * <p>
 * A target only counts as unreachable when the connection attempt itself failed. Before the targets are probed, the image is checked once with a connection to a listener inside
 * the
 * same container, so an image whose {@code nc} lacks the needed options leads to an inconclusive result and not to a false assurance.
 * <p>
 * The probe only reports: a reachable service is logged as an error with the remedy, and a probe that could not run is logged as information, because an agent without access to
 * the image registry is a normal case and must not stop the agent.
 */
public class BuildContainerIsolationProbe {

    private static final Logger log = LoggerFactory.getLogger(BuildContainerIsolationProbe.class);

    /**
     * The exit code of {@code nc -z} for a connection that was refused or timed out. Every other non-zero code, such as the usage error of an unsupported option, is no verdict.
     */
    private static final int EXIT_CONNECTION_FAILED = 1;

    private static final int CONNECT_TIMEOUT_SECONDS = 3;

    private static final int CONTAINER_TIMEOUT_SECONDS = 30;

    private static final int IMAGE_PULL_TIMEOUT_SECONDS = 60;

    private static final int MAX_PORT = 65535;

    /** The exit code of the probe of the host when the container has no entry for the name of the host. */
    private static final int EXIT_NO_HOST_ENTRY = 3;

    private static final int EXIT_NO_RESOLUTION = 4;

    /** Host names that are safe to put into the script of the test container. */
    private static final Pattern CONTAINER_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    /** The name that a container uses for the host of the agent. */
    private static final String HOST_NAME = "host.docker.internal";

    /** Runs a listener and connects to it in the same container, which shows that the {@code nc} of the image supports what the probe needs. */
    private static final String SELF_TEST_SCRIPT = "nc -l -p 18080 -e true & sleep 1; nc -w " + CONNECT_TIMEOUT_SECONDS + " -z 127.0.0.1 18080";

    /**
     * A service that build containers must not reach.
     *
     * @param host the host name or address
     * @param port the TCP port
     */
    public record Target(String host, int port) {

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    /**
     * The outcome of a probe.
     *
     * @param reachable    the targets a container could connect to
     * @param notReachable the targets a container could not connect to
     * @param inconclusive the targets that could not be checked, for example because the image has no suitable command
     */
    public record Result(List<Target> reachable, List<Target> notReachable, List<Target> inconclusive) {
    }

    private final DockerClient dockerClient;

    private final String image;

    /** Whether the image is available, known after the first check. A probe of several networks pulls at most once. */
    private Boolean imageAvailable;

    /** Whether the agent runs in a container itself. A loopback address then names the namespace of the agent, which the host of the Docker daemon does not share. */
    private final boolean agentInContainer;

    public BuildContainerIsolationProbe(DockerClient dockerClient, String image) {
        this(dockerClient, image, Files.exists(Path.of("/.dockerenv")) || Files.exists(Path.of("/run/.containerenv")));
    }

    BuildContainerIsolationProbe(DockerClient dockerClient, String image, boolean agentInContainer) {
        this.dockerClient = dockerClient;
        this.image = image;
        this.agentInContainer = agentInContainer;
    }

    /**
     * Parses {@code host:port} and {@code [IPv6]:port} entries and drops entries that cannot be checked from a container.
     * A loopback target names the host of the agent. It cannot be probed by its address, because inside a container the address names the container itself, so it is probed
     * through the address that a container uses for its host.
     *
     * @param entries the configured entries, may contain null or blank values
     * @return the targets that can be probed
     */
    public static List<Target> parseTargets(Collection<String> entries) {
        List<Target> targets = new ArrayList<>();
        if (entries == null) {
            return targets;
        }
        for (String entry : entries) {
            parseTarget(entry).filter(target -> !targets.contains(target)).ifPresent(targets::add);
        }
        return targets;
    }

    private static Optional<Target> parseTarget(String entry) {
        if (entry == null || entry.isBlank()) {
            return Optional.empty();
        }
        String trimmed = entry.trim();
        int separator = trimmed.lastIndexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            log.warn("Ignoring the network isolation target '{}': expected host:port", trimmed);
            return Optional.empty();
        }
        String host = trimmed.substring(0, separator);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        int port;
        try {
            port = Integer.parseInt(trimmed.substring(separator + 1));
        }
        catch (NumberFormatException _) {
            log.warn("Ignoring the network isolation target '{}': the port is not a number", trimmed);
            return Optional.empty();
        }
        if (port < 1 || port > MAX_PORT || host.isBlank()) {
            log.warn("Ignoring the network isolation target '{}': expected host:port", trimmed);
            return Optional.empty();
        }
        return Optional.of(new Target(host, port));
    }

    static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("::1") || normalized.equals("0:0:0:0:0:0:0:1") || normalized.startsWith("127.") || normalized.equals("0.0.0.0");
    }

    /**
     * Tries to connect to every target from a container on the given network and logs the outcome.
     *
     * @param network the network of the container, or null or blank for the default network of the container runtime
     * @param targets the services that must not be reachable
     * @return the outcome, with every target in exactly one of the lists
     */
    public Result run(String network, List<Target> targets) {
        List<Target> reachable = new ArrayList<>();
        List<Target> notReachable = new ArrayList<>();
        List<Target> inconclusive = new ArrayList<>();
        if (targets.isEmpty()) {
            log.debug("No targets for the build container network check");
            return new Result(reachable, notReachable, inconclusive);
        }
        String networkName = describe(network);
        if (!imageAvailable()) {
            inconclusive.addAll(targets);
            log.info("The build container network check was skipped: the image {} is not available on this agent", image);
            return new Result(reachable, notReachable, inconclusive);
        }
        Integer selfTestExitCode = runInContainer(network, false, "self test", "sh", "-c", SELF_TEST_SCRIPT);
        if (!Integer.valueOf(0).equals(selfTestExitCode)) {
            inconclusive.addAll(targets);
            log.info(
                    "The build container network check on the network '{}' could not decide: the test container {} (exit code {}). Is the network available on this agent, and does the image {} have 'nc'?",
                    networkName, selfTestExitCode == null ? "did not finish" : "failed", selfTestExitCode, image);
            return new Result(reachable, notReachable, inconclusive);
        }
        for (Target target : targets) {
            switch (probe(network, target)) {
                case REACHABLE -> reachable.add(target);
                case NOT_REACHABLE -> notReachable.add(target);
                case INCONCLUSIVE -> inconclusive.add(target);
            }
        }
        report(networkName, reachable, notReachable, inconclusive);
        return new Result(reachable, notReachable, inconclusive);
    }

    private enum Verdict {
        REACHABLE, NOT_REACHABLE, INCONCLUSIVE
    }

    /**
     * Connects to every address of the target. The names are resolved here, because the {@code nc} of the container makes one connection to one of the addresses of a name and
     * exits with the same code when a name cannot be resolved as when a connection is refused. A name is also tried inside the container, because a network can resolve it
     * differently, for example through an alias. That attempt can only show that the target is reachable: a failure of it says nothing, as it may be a failed resolution.
     */
    private Verdict probe(String network, Target target) {
        boolean loopbackName = isLoopback(target.host());
        List<String> addresses = loopbackName ? List.of() : resolve(target.host());
        boolean allRefused = loopbackName || !addresses.isEmpty();
        // An address of the host itself is reached through the name that a container uses for its host. The other addresses are tried as they are.
        if (loopbackName || addresses.stream().anyMatch(BuildContainerIsolationProbe::isLoopbackAddress)) {
            Verdict hostVerdict = probeThroughHostAddress(network, target);
            if (hostVerdict == Verdict.REACHABLE) {
                return Verdict.REACHABLE;
            }
            // A refusal by the host says nothing about a service in the namespace of an agent that runs in a container
            allRefused &= hostVerdict == Verdict.NOT_REACHABLE && !agentInContainer;
        }
        for (String address : addresses.stream().filter(address -> !isLoopbackAddress(address)).toList()) {
            Integer exitCode = runInContainer(network, false, target + " at " + address, "nc", "-w", String.valueOf(CONNECT_TIMEOUT_SECONDS), "-z", address,
                    String.valueOf(target.port()));
            if (Integer.valueOf(0).equals(exitCode)) {
                return Verdict.REACHABLE;
            }
            allRefused &= Integer.valueOf(EXIT_CONNECTION_FAILED).equals(exitCode);
        }
        if (!loopbackName && !isAddress(target.host()) && CONTAINER_NAME.matcher(target.host()).matches()) {
            // Every address that the container resolves is tried, because nc alone would connect to one of them
            String script = "addresses=$(nslookup " + target.host()
                    + " 2>/dev/null | awk '/^Name:/ {found=1} found && /^Address/ {print $NF}' | sort -u); [ -n \"$addresses\" ] || exit " + EXIT_NO_RESOLUTION
                    + "; for address in $addresses; do nc -w " + CONNECT_TIMEOUT_SECONDS + " -z \"$address\" " + target.port() + " && exit 0; done; exit " + EXIT_CONNECTION_FAILED;
            Integer exitCode = runInContainer(network, false, target.toString(), "sh", "-c", script);
            if (Integer.valueOf(0).equals(exitCode)) {
                return Verdict.REACHABLE;
            }
        }
        return allRefused ? Verdict.NOT_REACHABLE : Verdict.INCONCLUSIVE;
    }

    /**
     * Probes a service of the host of the agent that is configured with a loopback address. A container reaches the host through the gateway of its network, which Docker names
     * {@code host-gateway}. A service that listens on the loopback interface only is not reachable that way on Linux, a service that listens on every interface is. The addresses
     * of
     * the
     * host are read from the hosts file of the container, so that a missing entry is told apart from a refused connection: the {@code nc} of busybox exits with 1 for both.
     * Rootless Docker, networks without a route to the gateway, and a service that a container on the same network publishes are not covered.
     */
    private Verdict probeThroughHostAddress(String network, Target target) {
        String script = "addresses=$(awk '/" + HOST_NAME + "/ {print $1}' /etc/hosts); [ -n \"$addresses\" ] || exit " + EXIT_NO_HOST_ENTRY
                + "; for address in $addresses; do nc -w " + CONNECT_TIMEOUT_SECONDS + " -z \"$address\" " + target.port() + " && exit 0; done; exit " + EXIT_CONNECTION_FAILED;
        Integer exitCode = runInContainer(network, true, target + " on the host", "sh", "-c", script);
        if (Integer.valueOf(0).equals(exitCode)) {
            return Verdict.REACHABLE;
        }
        return Integer.valueOf(EXIT_CONNECTION_FAILED).equals(exitCode) ? Verdict.NOT_REACHABLE : Verdict.INCONCLUSIVE;
    }

    private static boolean isLoopbackAddress(String address) {
        try {
            InetAddress parsed = InetAddress.ofLiteral(address);
            return parsed.isLoopbackAddress() || parsed.isAnyLocalAddress();
        }
        catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static boolean isAddress(String host) {
        try {
            InetAddress.ofLiteral(host);
            return true;
        }
        catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static List<String> resolve(String host) {
        try {
            return Arrays.stream(InetAddress.getAllByName(host)).map(InetAddress::getHostAddress).distinct().toList();
        }
        catch (UnknownHostException e) {
            log.info("The build container network check could not resolve {}: {}", host, e.getMessage());
            return List.of();
        }
    }

    private void report(String networkName, List<Target> reachable, List<Target> notReachable, List<Target> inconclusive) {
        if (!reachable.isEmpty()) {
            log.error("Build containers on the network '{}' can reach {}. Student code runs in these containers, so the service has to be unreachable from the container network. "
                    + "Restrict the container subnets with firewall rules or move the service to a network that build containers cannot reach. "
                    + "See the network model in the administration documentation.", networkName, reachable);
        }
        if (!inconclusive.isEmpty()) {
            log.info("The build container network check on the network '{}' could not decide for {}: the connection test did not complete", networkName, inconclusive);
        }
        if (reachable.isEmpty() && inconclusive.isEmpty()) {
            log.info("Build containers on the network '{}' cannot reach {}", networkName, notReachable);
        }
    }

    private static String describe(String network) {
        return network == null || network.isBlank() ? "default" : network;
    }

    private boolean imageAvailable() {
        if (imageAvailable == null) {
            imageAvailable = ensureImage();
        }
        return imageAvailable;
    }

    private boolean ensureImage() {
        try (var inspectCommand = dockerClient.inspectImageCmd(image)) {
            inspectCommand.exec();
            return true;
        }
        catch (NotFoundException _) {
            return pullImage();
        }
        catch (Exception e) {
            log.debug("Could not inspect {} for the build container network check: {}", image, e.getMessage());
            return false;
        }
    }

    private boolean pullImage() {
        try (var pullCommand = dockerClient.pullImageCmd(image)) {
            try (var callback = pullCommand.start()) {
                if (!callback.awaitCompletion(IMAGE_PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    return false;
                }
            }
            try (var inspectCommand = dockerClient.inspectImageCmd(image)) {
                inspectCommand.exec();
            }
            return true;
        }
        catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (Exception e) {
            log.debug("Could not pull {} for the build container network check: {}", image, e.getMessage());
            return false;
        }
    }

    /**
     * Runs a command in a short-lived container and waits for it.
     *
     * @param network      the network of the container, or null or blank for the default network of the container runtime
     * @param withHostName whether the name of the host of the agent is made known to the container
     * @param description  what the command does, for the log
     * @param command      the command with its arguments
     * @return the exit code, or null if the container did not run to completion
     */
    private Integer runInContainer(String network, boolean withHostName, String description, String... command) {
        String containerId = null;
        try {
            // Without a network the container joins the default network, like a build container that does not request another one.
            HostConfig hostConfig = HostConfig.newHostConfig();
            if (network != null && !network.isBlank()) {
                hostConfig.withNetworkMode(network);
            }
            if (withHostName) {
                hostConfig.withExtraHosts(HOST_NAME + ":host-gateway");
            }
            try (CreateContainerCmd createCommand = dockerClient.createContainerCmd(image)) {
                CreateContainerResponse container = createCommand.withHostConfig(hostConfig).withCmd(command).exec();
                containerId = container.getId();
            }
            try (var startCommand = dockerClient.startContainerCmd(containerId)) {
                startCommand.exec();
            }
            return dockerClient.waitContainerCmd(containerId).start().awaitStatusCode(CONTAINER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            log.debug("The build container network check '{}' did not finish: {}", description, e.getMessage());
            return null;
        }
        finally {
            removeContainer(containerId);
        }
    }

    private void removeContainer(String containerId) {
        if (containerId == null) {
            return;
        }
        try (var removeCommand = dockerClient.removeContainerCmd(containerId)) {
            removeCommand.withForce(true).exec();
        }
        catch (Exception e) {
            log.debug("Could not remove the container {} of the build container network check: {}", containerId, e.getMessage());
        }
    }
}
