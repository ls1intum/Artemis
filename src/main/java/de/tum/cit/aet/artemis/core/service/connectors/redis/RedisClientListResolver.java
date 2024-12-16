package de.tum.cit.aet.artemis.core.service.connectors.redis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.redisson.client.RedisClient;
import org.redisson.client.protocol.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisClientListResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisClientListResolver.class);

    // TODO: When https://github.com/redisson/redisson/issues/6108 is resolved, this is also possible with the following code
    // redisConnectionFactory.getConnection().serverCommands().getClientList()
    private final RedisClient redisClient;

    public RedisClientListResolver(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public Set<String> getUniqueClients() {

        log.debug("Current redis client name: {}", redisClient.getConfig().getClientName());
        List<String> clients = redisClient.connect().sync(RedisCommands.CLIENT_LIST);

        // Parse the Redis client list to extract names and filter duplicates
        Set<String> uniqueClients = new HashSet<>();

        for (String clientInfo : clients) {
            String clientName = extractClientNameFromClientInfo(clientInfo);
            if (clientName != null && clientName.toLowerCase().startsWith("artemis")) {
                // Optional: Apply more complex logic here to choose the most relevant connection
                uniqueClients.add(clientName);
            }
        }

        log.debug("Redis client list based on names: {}", uniqueClients);

        return uniqueClients;
    }

    // Helper method to extract the 'name' field from the Redis client info
    private String extractClientNameFromClientInfo(String clientInfo) {
        String[] parts = clientInfo.split(" ");
        for (String part : parts) {
            if (part.startsWith("name=")) {
                return part.substring(5); // Extract the client name
            }
        }
        return null; // Return null if no name is found
    }

}
