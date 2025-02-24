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

    private final RedisConnectionFactory connectionFactory;

    public RedisClientListResolver(RedisConnectionFactory redisConnectionFactory) {
        this.connectionFactory = redisConnectionFactory;
    }

    public Set<String> getUniqueClients() {
        List<RedisClientInfo> clients = connectionFactory.getConnection().serverCommands().getClientList();

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

        log.debug("Redis client list based on names: {}", uniqueClients);

        return uniqueClients;
    }
}
