package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
     * @param addressesByClientName the Artemis client names that were observed, each mapped to the host addresses Redis accepted its connections from. The address comes
     *                                  from Redis' own {@code CLIENT LIST} view of the connection, so it is observed rather than self-reported and is therefore usable for
     *                                  authorizing git requests. The port is dropped: it is ephemeral and changes on every reconnect. A client that reconnects can briefly
     *                                  appear under two addresses, and clients behind one NAT gateway share an address, which is why the value is a set.
     * @param complete              whether the lookup covered the whole deployment. A single {@code CLIENT LIST} is answered by one Redis node only, so in cluster mode the
     *                                  result is complete only once every node has answered. Callers that conclude a node is gone from its <em>absence</em> must require
     *                                  this flag, because a partial answer is indistinguishable from a node that shut down.
     */
    public record ClientListSnapshot(Map<String, Set<String>> addressesByClientName, boolean complete) {

        static ClientListSnapshot incomplete() {
            return new ClientListSnapshot(Map.of(), false);
        }

        /**
         * @return the Artemis client names that were observed
         */
        public Set<String> clientNames() {
            return addressesByClientName.keySet();
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
     * Fetches the connected Artemis clients and maps each client name to the addresses it connects from.
     * <p>
     * A failed, timed out or - in cluster mode - partial lookup returns {@link Optional#empty()} rather than an empty or truncated map. The difference matters to
     * callers: a present map means Redis answered for the whole deployment and any client missing from it really is gone, while empty means Redis did not answer
     * completely. Conflating the two would let a two second timeout, or one unreachable cluster node, look like every build agent disconnecting.
     *
     * @return Artemis client name to the host addresses it is connected from, or empty if the client list could not be retrieved in full
     */
    public Optional<Map<String, Set<String>>> getClientAddressesByName() {
        ClientListSnapshot snapshot = resolveClients();
        return snapshot.complete() ? Optional.of(snapshot.addressesByClientName()) : Optional.empty();
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
            Map<String, Set<String>> addressesByClientName = artemisClientAddresses(clients);
            log.debug("Redis client list based on names: {}", addressesByClientName.keySet());
            return new ClientListSnapshot(addressesByClientName, true);
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

        Map<String, Set<String>> addressesByClientName = new HashMap<>();
        for (RedisClusterNode clusterNode : clusterNodes) {
            List<RedisClientInfo> clients = clusterConnection.serverCommands().getClientList(clusterNode).collectList().block(LOOKUP_TIMEOUT);
            if (clients == null) {
                log.error("Redis Cluster node {} did not return a client list, treating the client list as incomplete", clusterNode.getId());
                return ClientListSnapshot.incomplete();
            }
            // One client can hold connections to several cluster nodes, so the per-node address sets are merged rather than replaced.
            artemisClientAddresses(clients).forEach((clientName, addresses) -> addressesByClientName.computeIfAbsent(clientName, _ -> new HashSet<>()).addAll(addresses));
        }
        log.debug("Aggregated Redis client list across {} cluster nodes: {}", clusterNodes.size(), addressesByClientName.keySet());
        return new ClientListSnapshot(addressesByClientName, true);
    }

    private static Map<String, Set<String>> artemisClientAddresses(List<RedisClientInfo> clients) {
        Map<String, Set<String>> addressesByClientName = new HashMap<>();
        for (RedisClientInfo clientInfo : clients) {
            String clientName = clientInfo.getName();
            // TODO: also make this configurable via application properties?
            // Locale.ROOT, not the default locale: under tr_TR "I".toLowerCase() is the dotless "ı", so a client named
            // "ARTEMIS-core-1" would not match the prefix and the node would look like a foreign client to every caller.
            if (clientName == null || !clientName.toLowerCase(Locale.ROOT).startsWith(ARTEMIS_CLIENT_NAME_PREFIX)) {
                continue;
            }
            Set<String> addresses = addressesByClientName.computeIfAbsent(clientName, _ -> new HashSet<>());
            String host = hostOf(clientInfo.getAddressPort());
            if (host != null) {
                addresses.add(host);
            }
        }
        return addressesByClientName;
    }

    /**
     * Extracts the host from a Redis {@code addr} field, which is formatted as {@code host:port} for IPv4 and as
     * {@code [host]:port} for IPv6.
     *
     * @param addressPort the raw {@code addr} value, may be null
     * @return the host without the port, or {@code null} if it could not be determined
     */
    private static String hostOf(String addressPort) {
        if (addressPort == null || addressPort.isBlank()) {
            return null;
        }
        if (addressPort.startsWith("[")) {
            int closingBracket = addressPort.indexOf(']');
            return closingBracket > 1 ? addressPort.substring(1, closingBracket) : null;
        }
        // Split on the last colon so an unbracketed IPv6 address is not truncated at its first group
        int lastColon = addressPort.lastIndexOf(':');
        String host = lastColon > 0 ? addressPort.substring(0, lastColon) : addressPort;
        return host.isBlank() ? null : host;
    }
}
