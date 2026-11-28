package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

/** Sets the identity used by Redis membership lookups on every connection opened by this client. */
@Component
@Lazy
@Conditional(RedisCondition.class)
public class RedisClientNameCustomizer implements RedissonAutoConfigurationCustomizer {

    private static final String PREFIX = "artemis";

    @Override
    public void customize(Config config) {
        var servers = ConfigSupport.getConfig(config);
        String name = servers.getClientName();
        if (name == null || name.isBlank()) {
            servers.setClientName(PREFIX + "-" + UUID.randomUUID());
        }
        else if (!isArtemisClient(name)) {
            throw new IllegalArgumentException("spring.data.redis.client-name must start with 'artemis' and be unique for each Artemis process");
        }
    }

    static boolean isArtemisClient(@Nullable String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }
}
