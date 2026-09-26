package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

/** One process incarnation shared by the Redisson connection configuration and ownership tokens. */
@Lazy
@Component
@Conditional(RedisCondition.class)
public class RedisNodeIdentity {

    private static final String PREFIX = "artemis-coordination:";

    private final String connectionName;

    public RedisNodeIdentity(@Value("${spring.data.redis.client-name:artemis-node}") String displayName) {
        connectionName = PREFIX + UUID.randomUUID() + ":" + displayName;
    }

    /** @return the unique connection name Redis observes for this process incarnation */
    public String connectionName() {
        return connectionName;
    }

    /**
     * @param name a connection name observed by Redis
     * @return whether it carries a complete process-incarnation prefix
     */
    static boolean isCoordinationName(String name) {
        if (!name.startsWith(PREFIX) || name.length() <= PREFIX.length() + 37 || name.charAt(PREFIX.length() + 36) != ':') {
            return false;
        }
        try {
            return UUID.fromString(name.substring(PREFIX.length(), PREFIX.length() + 36)).toString().equals(name.substring(PREFIX.length(), PREFIX.length() + 36));
        }
        catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * @param name a connection name observed by Redis
     * @return the configured name used by existing node/build-agent monitoring
     */
    static String displayName(String name) {
        return isCoordinationName(name) ? name.substring(PREFIX.length() + 37) : name;
    }
}
