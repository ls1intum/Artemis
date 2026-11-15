package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.service.distributed.ClusterNodeInfo;
import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter;

/**
 * Runs a real, isolated Hazelcast member with the type filter applied and checks what it reads back.
 */
class HazelcastTypeFilterTest {

    private HazelcastInstance instance;

    @BeforeEach
    void startMember() {
        startMember(DistributedDataTypeFilter.withAdditional(List.of()));
    }

    private void startMember(DistributedDataTypeFilter filter) {
        shutdownMember();
        Config config = new Config();
        config.setClusterName("filter-test-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        HazelcastTypeFilter.apply(config.getSerializationConfig(), filter);
        instance = Hazelcast.newHazelcastInstance(config);
    }

    @AfterEach
    void shutdownMember() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    @Test
    void testReadsTheTypesArtemisStores() {
        IMap<String, Object> map = instance.getMap("allowed");
        var node = new ClusterNodeInfo("node", "host:1", "host", 1, "instance");
        map.put("record", node);
        map.put("list", new ArrayList<>(List.of("a", "b")));
        map.put("strings", new String[] { "a", "b" });

        assertThat(map.get("record")).isEqualTo(node);
        assertThat(map.get("list")).isEqualTo(List.of("a", "b"));
        assertThat((String[]) map.get("strings")).containsExactly("a", "b");
    }

    @Test
    void testRefusesATypeOutsideTheList() {
        IMap<String, Object> map = instance.getMap("rejected");
        map.put("uri", URI.create("https://example.org/"));

        assertThatThrownBy(() -> map.get("uri")).hasRootCauseInstanceOf(SecurityException.class).hasStackTraceContaining("java.net.URI");
    }

    @Test
    void testRefusesAnArrayOfATypeOutsideTheList() {
        IMap<String, Object> map = instance.getMap("rejected-array");
        map.put("uris", new URI[] { URI.create("https://example.org/") });

        assertThatThrownBy(() -> map.get("uris")).hasRootCauseInstanceOf(SecurityException.class).hasStackTraceContaining("java.net.URI");
    }

    @Test
    void testOperatorConfiguredTypeIsRead() {
        startMember(DistributedDataTypeFilter.withAdditional(List.of("java.net.URI")));
        IMap<String, Object> map = instance.getMap("configured");
        map.put("uri", URI.create("https://example.org/"));

        assertThat(map.get("uri")).isEqualTo(URI.create("https://example.org/"));
    }
}
