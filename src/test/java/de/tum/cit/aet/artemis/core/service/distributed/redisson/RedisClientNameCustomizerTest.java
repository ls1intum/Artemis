package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;

class RedisClientNameCustomizerTest {

    private final RedisClientNameCustomizer customizer = new RedisClientNameCustomizer();

    @Test
    void generatesDistinctDiscoverableNamesWhenUnconfigured() {
        var standalone = new Config();
        standalone.useSingleServer().setAddress("redis://localhost:6379");
        var clustered = new Config();
        clustered.useClusterServers().addNodeAddress("redis://localhost:6379");

        customizer.customize(standalone);
        customizer.customize(clustered);

        String first = ConfigSupport.getConfig(standalone).getClientName();
        String second = ConfigSupport.getConfig(clustered).getClientName();
        assertThat(first).startsWith("artemis-").isNotEqualTo(second);
        assertThat(RedisClientNameCustomizer.isArtemisClient(second)).isTrue();
    }

    @Test
    void keepsConfiguredIdentityAndRejectsNamesExcludedFromMembership() {
        var config = new Config();
        var server = config.useSingleServer().setAddress("redis://localhost:6379").setClientName("ARTEMIS-build");
        customizer.customize(config);
        assertThat(server.getClientName()).isEqualTo("ARTEMIS-build");

        server.setClientName("unrelated-client");
        assertThatIllegalArgumentException().isThrownBy(() -> customizer.customize(config)).withMessageContaining("spring.data.redis.client-name");
    }
}
