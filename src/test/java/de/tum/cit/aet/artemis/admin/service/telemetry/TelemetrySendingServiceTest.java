package de.tum.cit.aet.artemis.admin.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.NodeRegistryService;
import de.tum.cit.aet.artemis.localci.api.LocalCITelemetryApi;

class TelemetrySendingServiceTest {

    private final ProfileService profiles = mock(ProfileService.class);

    private final NodeRegistryService nodes = mock(NodeRegistryService.class);

    private final LocalCITelemetryApi agents = mock(LocalCITelemetryApi.class);

    private final RestTemplate rest = new RestTemplate();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();

    // Module flags that are not set read as disabled, so a newly added module does not need to be listed here.
    private final MockEnvironment env = new MockEnvironment() {

        @Override
        public <T> T getProperty(String key, Class<T> targetType) {
            T value = super.getProperty(key, targetType);
            return value == null && targetType == Boolean.class ? targetType.cast(Boolean.FALSE) : value;
        }
    };

    private TelemetrySendingService service;

    @BeforeEach
    void setUp() {
        env.setActiveProfiles("prod", "core", "scheduling");
        env.setProperty("artemis.iris.enabled", "true");
        service = sender(Optional.of(agents));
    }

    private TelemetrySendingService sender(Optional<LocalCITelemetryApi> api) {
        var sender = new TelemetrySendingService(env, rest, profiles, nodes, api);
        ReflectionTestUtils.setField(sender, "version", "10.0.0");
        ReflectionTestUtils.setField(sender, "serverUrl", "https://artemis.example");
        ReflectionTestUtils.setField(sender, "operator", "Operator");
        ReflectionTestUtils.setField(sender, "operatorAdminName", "Erika Muster");
        ReflectionTestUtils.setField(sender, "universityName", "University");
        ReflectionTestUtils.setField(sender, "operatorContact", "admin@example.org");
        ReflectionTestUtils.setField(sender, "destination", "https://telemetry.example");
        ReflectionTestUtils.setField(sender, "datasourceUrl", "jdbc:postgresql://localhost/artemis");
        return sender;
    }

    @Test
    void reportsLiveTopologyFeaturesAndStartupIdentity() {
        when(nodes.getLiveNodeIds()).thenReturn(Set.of("node1", "node2"));
        when(agents.getConnectedBuildAgentCount()).thenReturn(3);
        var data = service.buildTelemetryData(true, "startup-id", Instant.EPOCH);
        assertThat(data.numberOfNodes()).isEqualTo(2);
        assertThat(data.buildAgentCount()).isEqualTo(3);
        assertThat(data.isMultiNode()).isTrue();
        assertThat(data.moduleFeatures()).contains("iris");
        assertThat(data.universityName()).isEqualTo("University");
        assertThat(data.adminName()).isEqualTo("Erika Muster");
        assertThat(data.startupId()).isEqualTo("startup-id");
        assertThat(data.startedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void keepsUnknownTopologyUnknownAndOmitsPersonalData() {
        when(nodes.getLiveNodeIds()).thenReturn(Set.of());
        when(agents.getConnectedBuildAgentCount()).thenReturn(null);
        var data = service.buildTelemetryData(false, "startup-id", Instant.EPOCH);
        assertThat(data.numberOfNodes()).isNull();
        assertThat(data.isMultiNode()).isNull();
        assertThat(data.buildAgentCount()).isNull();
        assertThat(data.adminName()).isNull();
        assertThat(data.contact()).isNull();
    }

    @Test
    void standaloneWithoutLocalCiHasNoBuildAgents() {
        when(nodes.getLiveNodeIds()).thenReturn(Set.of("node1"));
        var data = sender(Optional.empty()).buildTelemetryData(false, "startup-id", Instant.EPOCH);
        assertThat(data.numberOfNodes()).isEqualTo(1);
        assertThat(data.isMultiNode()).isFalse();
        assertThat(data.buildAgentCount()).isZero();
    }

    @Test
    void collectionFailureDoesNotInventCountsOrPreventOtherTelemetry() {
        when(nodes.getLiveNodeIds()).thenThrow(new IllegalStateException("disconnected"));
        when(agents.getConnectedBuildAgentCount()).thenThrow(new IllegalStateException("disconnected"));
        var data = service.buildTelemetryData(false, "startup-id", Instant.EPOCH);
        assertThat(data.numberOfNodes()).isNull();
        assertThat(data.buildAgentCount()).isNull();
        assertThat(data.version()).isEqualTo("10.0.0");
    }

    @Test
    void postsTheReportAsJson() {
        when(nodes.getLiveNodeIds()).thenReturn(Set.of("node1", "node2"));
        when(agents.getConnectedBuildAgentCount()).thenReturn(3);
        server.expect(requestTo("https://telemetry.example/api/telemetry")).andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.universityName").value("University")).andExpect(jsonPath("$.moduleFeatures[0]").value("iris"))
                .andExpect(jsonPath("$.numberOfNodes").value(2)).andExpect(jsonPath("$.buildAgentCount").value(3)).andExpect(jsonPath("$.isMultiNode").value(true))
                .andExpect(jsonPath("$.startupId").value("startup-id")).andExpect(jsonPath("$.startedAt").value("1970-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.adminName").doesNotExist()).andExpect(jsonPath("$.contact").doesNotExist()).andRespond(withSuccess());
        service.sendTelemetryByPostRequest(false, "startup-id", Instant.EPOCH);
        server.verify();
    }

    @Test
    void omitsEmptyAndUnknownValues() {
        env.setProperty("artemis.iris.enabled", "false");
        when(nodes.getLiveNodeIds()).thenReturn(Set.of());
        when(agents.getConnectedBuildAgentCount()).thenReturn(null);
        server.expect(requestTo("https://telemetry.example/api/telemetry")).andExpect(jsonPath("$.moduleFeatures").doesNotExist())
                .andExpect(jsonPath("$.numberOfNodes").doesNotExist()).andExpect(jsonPath("$.buildAgentCount").doesNotExist()).andRespond(withSuccess());
        service.sendTelemetryByPostRequest(false, "startup-id", Instant.EPOCH);
        server.verify();
    }

    @Test
    void sendsJsonAndHandlesHttpFailure() {
        server.expect(requestTo("https://telemetry.example/api/telemetry")).andExpect(content().contentType("application/json")).andRespond(withSuccess());
        service.sendTelemetryByPostRequest(false, "startup-id", Instant.EPOCH);
        server.verify();
        server.reset();
        server.expect(requestTo("https://telemetry.example/api/telemetry")).andRespond(withServerError());
        assertThatNoException().isThrownBy(() -> service.sendTelemetryByPostRequest(false, "startup-id", Instant.EPOCH));
        server.verify();
    }
}
