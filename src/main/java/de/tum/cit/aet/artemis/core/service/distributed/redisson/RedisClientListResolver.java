package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

@Lazy
@Component
@Conditional(RedisCondition.class)
public class RedisClientListResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisClientListResolver.class);

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);

    private static final String ARTEMIS_CLIENT_NAME_PREFIX = "artemis";

    private final ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    public RedisClientListResolver(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        this.reactiveRedisConnectionFactory = reactiveRedisConnectionFactory;
    }

    /**
     * Result of a client list lookup.
     *
     * @param clientNames the Artemis client names that were observed
     * @param complete    whether the lookup covered the whole deployment. A single {@code CLIENT LIST} is answered by one Redis node only, so in cluster mode the
     *                        result is complete only once every node has answered. Callers that conclude a node is gone from its <em>absence</em> must require this
     *                        flag, because a partial answer is indistinguishable from a node that shut down.
     */
    public record ClientListSnapshot(Set<String> clientNames, boolean complete) {

        static ClientListSnapshot incomplete() {
            return new ClientListSnapshot(Set.of(), false);
        }
    }

    /**
     * Fetches the names of the connected Artemis clients.
     *
     * @return the observed client names, which may be a partial view in Redis Cluster mode
     */
    public Set<String> getUniqueClients() {
        return resolveClients().clientNames();
    }

    /**
     * Fetches the names of the connected Artemis clients together with the information whether the whole deployment was covered.
     * <p>
     * In standalone mode one {@code CLIENT LIST} sees every client, so a single successful call is complete. In cluster mode each node only knows its own clients,
     * so the lists of all cluster nodes are aggregated and the result counts as complete only if every one of them answered.
     *
     * @return the snapshot of connected Artemis clients
     */
    public ClientListSnapshot resolveClients() {
        try (ReactiveRedisConnection connection = reactiveRedisConnectionFactory.getReactiveConnection()) {
            if (connection instanceof ReactiveRedisClusterConnection clusterConnection) {
                return resolveClusterClients(clusterConnection);
            }
            List<RedisClientInfo> clients = connection.serverCommands().getClientList().collectList().block(LOOKUP_TIMEOUT);
            if (clients == null) {
                log.error("Redis client list is null");
                return ClientListSnapshot.incomplete();
            }
            Set<String> clientNames = artemisClientNames(clients);
            log.debug("Redis client list based on names: {}", clientNames);
            return new ClientListSnapshot(clientNames, true);
        }
        catch (RuntimeException e) {
            log.error("Failed to fetch Redis client list within timeout", e);
            return ClientListSnapshot.incomplete();
        }
    }

    private ClientListSnapshot resolveClusterClients(ReactiveRedisClusterConnection clusterConnection) {
        List<RedisClusterNode> clusterNodes = clusterConnection.clusterGetNodes().collectList().block(LOOKUP_TIMEOUT);
        if (clusterNodes == null || clusterNodes.isEmpty()) {
            log.error("Could not determine the Redis Cluster topology, treating the client list as incomplete");
            return ClientListSnapshot.incomplete();
        }

        Set<String> clientNames = new HashSet<>();
        for (RedisClusterNode clusterNode : clusterNodes) {
            List<RedisClientInfo> clients = clusterConnection.serverCommands().getClientList(clusterNode).collectList().block(LOOKUP_TIMEOUT);
            if (clients == null) {
                log.error("Redis Cluster node {} did not return a client list, treating the client list as incomplete", clusterNode.getId());
                return ClientListSnapshot.incomplete();
            }
            clientNames.addAll(artemisClientNames(clients));
        }
        log.debug("Aggregated Redis client list across {} cluster nodes: {}", clusterNodes.size(), clientNames);
        return new ClientListSnapshot(clientNames, true);
    }

    private static Set<String> artemisClientNames(List<RedisClientInfo> clients) {
        Set<String> clientNames = new HashSet<>();
        for (RedisClientInfo clientInfo : clients) {
            String clientName = clientInfo.getName();
            // TODO: also make this configurable via application properties?
            // Locale.ROOT, not the default locale: under tr_TR "I".toLowerCase() is the dotless "ı", so a client named
            // "ARTEMIS-core-1" would not match the prefix and the node would look like a foreign client to every caller.
            if (clientName != null && clientName.toLowerCase(Locale.ROOT).startsWith(ARTEMIS_CLIENT_NAME_PREFIX)) {
                clientNames.add(clientName);
            }
        }
        return clientNames;
    }
}
