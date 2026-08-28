package de.tum.cit.aet.artemis.core.service.distributed;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Tracks which Artemis nodes are currently alive, independently of the distributed data backend.
 *
 * <p>
 * <strong>Why this is not a provider method.</strong> The two backends answer "who is alive" in incompatible ways:
 * Hazelcast reports cluster members (which excludes build agents, since they connect as clients), while the Redis
 * provider infers it from {@code CLIENT LIST} filtered on a name prefix, which is fragile and returns only the clients of
 * a single node in Redis Cluster mode. Building the registry on an expiring map plus a heartbeat instead gives one
 * definition of liveness that behaves identically everywhere, and it needs no backend-specific code at all.
 *
 * <p>
 * <strong>Liveness definition.</strong> A node republishes its entry every {@link #HEARTBEAT_INTERVAL_SECONDS} seconds
 * into a map whose entries expire after {@link #NODE_TIMEOUT}. A node is therefore considered gone once it has missed
 * roughly three consecutive heartbeats, which tolerates a slow node or a brief network blip without flapping.
 */
@Lazy(false)
@Service
@Profile(PROFILE_CORE)
public class NodeRegistryService {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistryService.class);

    private static final String NODES_MAP = "clusterNodes";

    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    /**
     * Deliberately more than three heartbeat intervals, so a single missed or slow heartbeat never evicts a live node.
     */
    private static final Duration NODE_TIMEOUT = Duration.ofSeconds(35);

    private final Optional<DistributedDataProvider> distributedDataProvider;

    private DistributedMap<String, ClusterNodeInfo> nodes;

    private final String instanceId;

    public NodeRegistryService(Optional<DistributedDataProvider> distributedDataProvider, @Value("${eureka.instance.instanceId:}") String instanceId) {
        this.distributedDataProvider = distributedDataProvider;
        this.instanceId = instanceId;
    }

    /**
     * Lazily resolves the backing map, because the provider connects asynchronously and may not be usable while the
     * context is still starting.
     *
     * @return the expiring map of live nodes, or empty if no provider is available
     */
    private Optional<DistributedMap<String, ClusterNodeInfo>> nodesMap() {
        if (nodes == null) {
            if (distributedDataProvider.isEmpty()) {
                return Optional.empty();
            }
            try {
                nodes = distributedDataProvider.get().getExpiringMap(NODES_MAP, NODE_TIMEOUT);
            }
            catch (Exception e) {
                // The provider connects asynchronously, so resolving the map can fail while a node is still starting.
                // Returning empty keeps the documented fallback and lets a later heartbeat recover.
                log.debug("Distributed data provider is not usable yet, node registry unavailable: {}", e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.of(nodes);
    }

    /**
     * @return the identifier this node publishes itself under, or {@code unknown-node} if no provider is available
     */
    public String getLocalNodeId() {
        return distributedDataProvider.map(DistributedDataProvider::getLocalMemberAddress).orElse("unknown-node");
    }

    /**
     * Republishes this node's entry so that it does not expire.
     */
    @Scheduled(initialDelay = 0, fixedRate = HEARTBEAT_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        nodesMap().ifPresent(map -> {
            try {
                map.put(getLocalNodeId(), describeLocalNode());
            }
            catch (Exception e) {
                // A failed heartbeat is recoverable: the next one restores the entry, and until then this node is simply
                // reported as gone. Never propagate, or the scheduler would stop invoking this method.
                log.warn("Failed to publish node heartbeat: {}", e.getMessage());
            }
        });
    }

    /**
     * Describes this node for publication. The address format depends on the provider: Hazelcast reports
     * {@code [host]:port}, the Redis provider reports its configured client name, which has no port.
     *
     * @return this node's published description
     */
    private ClusterNodeInfo describeLocalNode() {
        String nodeId = getLocalNodeId();
        String host = nodeId;
        int port = 0;
        int portSeparator = nodeId.lastIndexOf(':');
        if (portSeparator > 0) {
            try {
                port = Integer.parseInt(nodeId.substring(portSeparator + 1));
                host = nodeId.substring(0, portSeparator).replace("[", "").replace("]", "");
            }
            catch (NumberFormatException e) {
                // Not a host:port identifier; keep the whole identifier as the host.
            }
        }
        return new ClusterNodeInfo(nodeId, nodeId, host, port, instanceId);
    }

    /**
     * @return descriptions of all nodes that have published a heartbeat recently
     */
    public Collection<ClusterNodeInfo> getLiveNodes() {
        return nodesMap().<Collection<ClusterNodeInfo>>map(DistributedMap::values).orElseGet(List::of);
    }

    /**
     * @return the identifiers of all nodes that have published a heartbeat recently, or an empty set if unknown
     */
    public Set<String> getLiveNodeIds() {
        return nodesMap().<Set<String>>map(DistributedMap::keySet).orElseGet(Set::of);
    }

    /**
     * @param nodeId the node identifier to check
     * @return true if the given node has published a heartbeat recently
     */
    public boolean isNodeAlive(String nodeId) {
        return nodesMap().map(map -> map.get(nodeId) != null).orElse(false);
    }

    /**
     * Removes this node's entry on shutdown so that a graceful stop is visible immediately instead of after the timeout.
     */
    @PreDestroy
    public void deregister() {
        nodesMap().ifPresent(map -> {
            try {
                map.remove(getLocalNodeId());
            }
            catch (Exception e) {
                log.debug("Could not deregister node on shutdown: {}", e.getMessage());
            }
        });
    }
}
