package de.tum.cit.aet.artemis.core.service.connectors.redis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.stereotype.Component;

@Component
public class RedisClientListResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisClientListResolver.class);

    // When https://github.com/redisson/redisson/issues/6108 is resolved, this is also possible with the following code
    private final RedisConnectionFactory redisConnectionFactory;

    // redisConnectionFactory.getConnection().serverCommands().getClientList()

    public RedisClientListResolver(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public Set<String> getUniqueClients() {
        List<RedisClientInfo> clients = redisConnectionFactory.getConnection().serverCommands().getClientList();

        // Parse the Redis client list to extract names and filter duplicates
        Set<String> uniqueClients = new HashSet<>();
        if (clients == null) {
            log.error("Redis client list is null");
            return uniqueClients;
        }

        for (RedisClientInfo clientInfo : clients) {
            String clientName = clientInfo.getName();
            if (clientName.toLowerCase().startsWith("artemis")) {
                // Optional: Apply more complex logic here to choose the most relevant connection
                uniqueClients.add(clientName);
            }
        }

        log.info("Redis client list based on names: {}", uniqueClients);

        return uniqueClients;
    }

}
