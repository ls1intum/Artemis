package de.tum.cit.aet.artemis.localci.service.distributed.redisson;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

@Lazy
@Component
@Conditional(RedisCondition.class)
public class RedisClientListResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisClientListResolver.class);

    private final ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    public RedisClientListResolver(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        this.reactiveRedisConnectionFactory = reactiveRedisConnectionFactory;
    }

    /**
     * Fetches the list of connected Redis clients and extracts unique client names that start with "artemis".
     * This method blocks for up to 2 seconds to get the client list.
     *
     * @return a set of unique client names starting with "artemis"
     */
    public Set<String> getUniqueClients() {
        Set<String> uniqueClients = getClientAddressesByName().keySet();
        log.debug("Redis client list based on names: {}", uniqueClients);

        return uniqueClients;
    }

    /**
     * Fetches the connected Redis clients and maps each Artemis client name to the addresses it connects from.
     * <p>
     * The address comes from Redis' own {@code CLIENT LIST} view of the connection, so it is observed rather than
     * self-reported and is therefore usable for authorizing git requests. The port is dropped: it is ephemeral and
     * changes on every reconnect.
     * <p>
     * A client that reconnects can briefly appear under two addresses, and clients behind one NAT gateway share an
     * address, so the value is a set.
     *
     * @return Artemis client name to the host addresses it is connected from; empty if the client list is unavailable
     */
    public Map<String, Set<String>> getClientAddressesByName() {
        List<RedisClientInfo> clients;
        try {
            clients = reactiveRedisConnectionFactory.getReactiveConnection().serverCommands().getClientList().collectList().block(Duration.ofSeconds(2));
        }
        catch (RuntimeException e) {
            log.error("Failed to fetch Redis client list within timeout", e);
            return Map.of();
        }

        if (clients == null) {
            log.error("Redis client list is null");
            return Map.of();
        }

        Map<String, Set<String>> addressesByClientName = new HashMap<>();
        for (RedisClientInfo clientInfo : clients) {
            String clientName = clientInfo.getName();
            // TODO: also make this configurable via application properties?
            if (clientName == null || !clientName.toLowerCase().startsWith("artemis")) {
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
