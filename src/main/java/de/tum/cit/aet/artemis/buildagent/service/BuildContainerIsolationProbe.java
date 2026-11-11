package de.tum.cit.aet.artemis.buildagent.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
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
 * The probe only reports: a reachable service is logged as an error with the remedy, and a probe that could not run is logged as information, because an agent without access to
 * the image registry is a normal case and must not stop the agent.
 */
public class BuildContainerIsolationProbe {

    private static final Logger log = LoggerFactory.getLogger(BuildContainerIsolationProbe.class);

    /** Exit codes of a shell that did not find or could not run the command, so that the connection was never tried. */
    private static final int EXIT_COMMAND_NOT_EXECUTABLE = 126;

    private static final int EXIT_COMMAND_NOT_FOUND = 127;

    private static final int CONNECT_TIMEOUT_SECONDS = 3;

    private static final int CONTAINER_TIMEOUT_SECONDS = 30;

    private static final int IMAGE_PULL_TIMEOUT_SECONDS = 60;

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

    public BuildContainerIsolationProbe(DockerClient dockerClient, String image) {
        this.dockerClient = dockerClient;
        this.image = image;
    }

    /**
     * Parses {@code host:port} and {@code [IPv6]:port} entries and drops entries that cannot be checked from a container.
     * Loopback addresses are dropped: inside a container they name the container itself, so a failed connection says nothing about the host.
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
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            int separator = trimmed.lastIndexOf(':');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                log.warn("Ignoring the network isolation target '{}': expected host:port", trimmed);
                continue;
            }
            String host = trimmed.substring(0, separator);
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            int port;
            try {
                port = Integer.parseInt(trimmed.substring(separator + 1));
            }
            catch (NumberFormatException e) {
                log.warn("Ignoring the network isolation target '{}': the port is not a number", trimmed);
                continue;
            }
            if (port < 1 || port > 65535 || host.isBlank()) {
                log.warn("Ignoring the network isolation target '{}': expected host:port", trimmed);
                continue;
            }
            if (isLoopback(host)) {
                log.debug("Not probing the loopback target {}: a container cannot reach the loopback interface of the host", trimmed);
                continue;
            }
            Target target = new Target(host, port);
            if (!targets.contains(target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private static boolean isLoopback(String host) {
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
        if (!ensureImage()) {
            inconclusive.addAll(targets);
            log.info("The build container network check was skipped: the image {} is not available on this agent", image);
            return new Result(reachable, notReachable, inconclusive);
        }
        for (Target target : targets) {
            Integer exitCode = connect(network, target);
            if (exitCode == null || exitCode == EXIT_COMMAND_NOT_EXECUTABLE || exitCode == EXIT_COMMAND_NOT_FOUND) {
                inconclusive.add(target);
            }
            else if (exitCode == 0) {
                reachable.add(target);
            }
            else {
                notReachable.add(target);
            }
        }
        report(network, reachable, notReachable, inconclusive);
        return new Result(reachable, notReachable, inconclusive);
    }

    private void report(String network, List<Target> reachable, List<Target> notReachable, List<Target> inconclusive) {
        if (!reachable.isEmpty()) {
            log.error("Build containers on the network '{}' can reach {}. Student code runs in these containers, so the service has to be unreachable from the container network. "
                    + "Restrict the container subnets with firewall rules or move the service to a network that build containers cannot reach. See the network model in the administration documentation.",
                    describe(network), reachable);
        }
        if (!inconclusive.isEmpty()) {
            log.info("The build container network check on the network '{}' could not decide for {}: the container did not finish, or the image {} has no 'nc' command",
                    describe(network), inconclusive, image);
        }
        if (reachable.isEmpty() && inconclusive.isEmpty()) {
            log.info("Build containers on the network '{}' cannot reach {}", describe(network), notReachable);
        }
    }

    private static String describe(String network) {
        return network == null || network.isBlank() ? "default" : network;
    }

    private boolean ensureImage() {
        try {
            dockerClient.inspectImageCmd(image).exec();
            return true;
        }
        catch (NotFoundException e) {
            try {
                dockerClient.pullImageCmd(image).start().awaitCompletion(IMAGE_PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                dockerClient.inspectImageCmd(image).exec();
                return true;
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            catch (Exception pullFailure) {
                log.debug("Could not pull {} for the build container network check: {}", image, pullFailure.getMessage());
                return false;
            }
        }
        catch (Exception e) {
            log.debug("Could not inspect {} for the build container network check: {}", image, e.getMessage());
            return false;
        }
    }

    /**
     * Starts a container that tries to connect to the target and waits for it.
     *
     * @param network the network of the container, or null or blank for the default network of the container runtime
     * @param target  the service to connect to
     * @return the exit code of the connection attempt, or null if the container did not finish
     */
    private Integer connect(String network, Target target) {
        String containerId = null;
        try {
            // Without a network the container joins the default network, like a build container that does not request another one.
            HostConfig hostConfig = HostConfig.newHostConfig();
            if (network != null && !network.isBlank()) {
                hostConfig.withNetworkMode(network);
            }
            CreateContainerResponse container = dockerClient.createContainerCmd(image).withHostConfig(hostConfig)
                    .withCmd("nc", "-w", String.valueOf(CONNECT_TIMEOUT_SECONDS), "-z", target.host(), String.valueOf(target.port())).exec();
            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();
            return dockerClient.waitContainerCmd(containerId).start().awaitStatusCode(CONTAINER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            log.debug("The build container network check for {} did not finish: {}", target, e.getMessage());
            return null;
        }
        finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                }
                catch (Exception e) {
                    log.debug("Could not remove the container {} of the build container network check: {}", containerId, e.getMessage());
                }
            }
        }
    }
}
