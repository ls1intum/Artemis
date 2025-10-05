package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
        List<RedisClientInfo> clients;
        try {
            clients = reactiveRedisConnectionFactory.getReactiveConnection().serverCommands().getClientList().collectList().block(Duration.ofSeconds(2));
        }
        catch (RuntimeException e) {
            log.error("Failed to fetch Redis client list within timeout", e);
            return Collections.emptySet();
        }

        Set<String> uniqueClients = new HashSet<>();
        if (clients == null) {
            log.error("Redis client list is null");
            return uniqueClients;
        }
        for (RedisClientInfo clientInfo : clients) {
            String clientName = clientInfo.getName();
            // TODO: also make this configurable via application properties?
            if (clientName.toLowerCase().startsWith("artemis")) {
                uniqueClients.add(clientName);
            }
        }
        log.debug("Redis client list based on names: {}", uniqueClients);

        return uniqueClients;
    }
}
