package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Hazelcast topic payload that asks a specific build agent to perform a maintenance action.
 * <p>
 * The maintenance topic is a single broadcast channel — every build agent in the cluster receives every message.
 * Each agent compares {@link #agentShortName()} with its own short name and ignores messages addressed to a
 * different agent. The same pattern is used by {@code pauseBuildAgentTopic} and {@code resumeBuildAgentTopic}
 * (see {@code SharedQueueProcessingService} listener registration), but instead of routing on a topic name, we
 * route on the {@link Type type} field inside one shared topic.
 * <p>
 * Action semantics live with the consuming services on the build-agent side:
 * <ul>
 * <li>{@link Type#RUN_CACHE_CLEANUP} → {@code BuildContainerCacheCleanupService.runCleanup()}: the same age + size
 * eviction the daily scheduler runs, on demand for one agent.</li>
 * <li>{@link Type#WIPE_MAVEN_CACHE} / {@link Type#WIPE_GRADLE_CACHE} →
 * {@code BuildContainerCacheCleanupService.wipeMavenCache()} / {@code wipeGradleCache()}: delete every file under
 * the cache root; useful when artifacts are suspected corrupt.</li>
 * <li>{@link Type#CLEAR_DOCKER_IMAGES} → {@code BuildAgentDockerService.clearAllUnusedDockerImages()}: remove every
 * Docker image that is not currently bound to a running container, regardless of its age.</li>
 * </ul>
 * Each action carries the existing pause / drain / resume semantics through
 * {@code SharedQueueProcessingService.pauseForMaintenance()}.
 *
 * @param agentShortName the short name of the agent that should execute the action; the other agents see this
 *                           message and ignore it because the name does not match their own
 *                           {@code buildAgentShortName}
 * @param type           which action to perform; see {@link Type}
 */
public record BuildAgentMaintenanceAction(String agentShortName, Type type) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        RUN_CACHE_CLEANUP, WIPE_MAVEN_CACHE, WIPE_GRADLE_CACHE, CLEAR_DOCKER_IMAGES
    }
}
