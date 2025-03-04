package de.tum.cit.aet.artemis.core.service.connectors.redis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.stereotype.Component;

@Component
public class RedisClientListResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisClientListResolver.class);

    // https://github.com/redisson/redisson/issues/6108 is resolved
    private final ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    public RedisClientListResolver(RedisConnectionFactory redisConnectionFactory, ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        this.reactiveRedisConnectionFactory = reactiveRedisConnectionFactory;
    }

    public Set<String> getUniqueClients() {
        List<RedisClientInfo> clients = reactiveRedisConnectionFactory.getReactiveConnection().serverCommands().getClientList().collectList().block();

        // Parse the Redis client list to extract names and filter duplicates
        Set<String> uniqueClients = new HashSet<>();
        if (clients == null) {
            log.error("Redis client list is null");
            return uniqueClients;
        }
        // reactiveRedisConnectionFactory because when using redisConnectionFactory the client list command returns just a list of strings
        for (RedisClientInfo clientInfo : clients) {
            String clientName = clientInfo.getName();
            // would be way to parse the string instead of helper methods if for some reason need to use redsConnectionFactory (TODO: check if this is redisson bug and if there are
            // any downsides of using reactive one
            // String clientName = RedisClientInfo.RedisClientInfoBuilder.fromString((String) clientInfo).getName();
            if (clientName.toLowerCase().startsWith("artemis")) {
                // Optional: Apply more complex logic here to choose the most relevant connection
                uniqueClients.add(clientName);
            }
        }

        log.info("Redis client list based on names: {}", uniqueClients);

        return uniqueClients;
    }

}
