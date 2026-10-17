package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionFunction;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionOn;

class HazelcastGenerationMapTest {

    @Test
    void generationJobsDoNotExpireAndProtectReadsAndWrites() {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment(), Optional.empty());
        Config config = new Config();
        config.addMapConfig(new MapConfig("default").setBackupCount(1));

        ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", config);

        var jobs = config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP);
        assertThat(jobs.getTimeToLiveSeconds()).isZero();
        assertThat(jobs.getMaxIdleSeconds()).isZero();
        assertThat(jobs.getEvictionConfig().getEvictionPolicy()).isEqualTo(EvictionPolicy.NONE);
        assertThat(jobs.getBackupCount()).isEqualTo(1);
        var protection = config.getSplitBrainProtectionConfig(jobs.getSplitBrainProtectionName());
        assertThat(protection.isEnabled()).isTrue();
        assertThat(protection.getProtectOn()).isEqualTo(SplitBrainProtectionOn.READ_WRITE);
        assertThat(config.getMapConfig(Constants.HYPERION_LIVE_SAVE_COORDINATION_MAP).getSplitBrainProtectionName()).isEqualTo(jobs.getSplitBrainProtectionName());
        var member = member(1, false);
        assertThat(protection.getFunctionImplementation().apply(List.of(member))).isTrue();
        assertThat(protection.getFunctionImplementation().apply(List.of(member, member(1, false)))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void onlyTheConfiguredDataMemberCountPermitsOperations(int expected) {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment().withProperty("jhipster.cache.hazelcast.expected-data-member-count", Integer.toString(expected)), Optional.empty());
        Config config = new Config();
        config.addMapConfig(new MapConfig("default").setBackupCount(1));
        ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", config);
        var function = config.getSplitBrainProtectionConfig(config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP).getSplitBrainProtectionName()).getFunctionImplementation();
        var members = new java.util.ArrayList<Member>();
        for (int i = 0; i < expected; i++) {
            members.add(member(expected, false));
        }
        assertThat(function.apply(members)).isTrue();
        members.add(member(expected, false));
        assertThat(function.apply(members)).isFalse();
        members.removeLast();
        members.removeLast();
        assertThat(function.apply(members)).isFalse();
    }

    @Test
    void liteMembersDoNotCountAsDataMembers() {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment(), Optional.empty());
        Config config = new Config();
        ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", config);
        var function = config.getSplitBrainProtectionConfig(config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP).getSplitBrainProtectionName()).getFunctionImplementation();
        Member lite = member(1, false);
        when(lite.isLiteMember()).thenReturn(true);
        assertThat(function.apply(List.of(member(1, false), lite))).isTrue();
    }

    @Test
    void invalidMemberCountFailsConfiguration() {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment().withProperty("jhipster.cache.hazelcast.expected-data-member-count", "0"), Optional.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", new Config()))
                .withMessage("Configure a positive Hazelcast expected-data-member-count");
    }

    @Test
    void exactMembershipFunctionSurvivesConfigurationSerialization() throws Exception {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment(), Optional.empty());
        Config config = new Config();
        ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", config);
        var original = config.getSplitBrainProtectionConfig(config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP).getSplitBrainProtectionName()).getFunctionImplementation();
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (SplitBrainProtectionFunction) in.readObject();
            assertThat(restored.apply(List.of(member(1, false)))).isTrue();
            assertThat(restored.apply(List.of(member(1, false), member(1, false)))).isFalse();
        }
    }

    @Test
    void mixedMemberCountsAndGenerationFlagsFailClosed() {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment().withProperty("jhipster.cache.hazelcast.expected-data-member-count", "2"), Optional.empty());
        Config config = new Config();
        ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", config);
        var function = config.getSplitBrainProtectionConfig(config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP).getSplitBrainProtectionName()).getFunctionImplementation();
        assertThat(function.apply(List.of(member(2, false), member(2, false)))).isTrue();
        assertThat(function.apply(List.of(member(2, false), member(1, false)))).isFalse();
        assertThat(function.apply(List.of(member(2, false), member(2, true)))).isFalse();
    }

    private static Member member(int expected, boolean enabled) {
        Member member = mock(Member.class);
        when(member.getAttribute("artemis.hyperion.expected-data-members")).thenReturn(Integer.toString(expected));
        when(member.getAttribute("artemis.hyperion.exercise-generation-enabled")).thenReturn(Boolean.toString(enabled));
        return member;
    }

}
