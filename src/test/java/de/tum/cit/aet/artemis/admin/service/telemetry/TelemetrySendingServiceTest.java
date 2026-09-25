package de.tum.cit.aet.artemis.admin.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.localci.api.LocalCITelemetryApi;

class TelemetrySendingServiceTest {

    private static final java.util.List<String> MODULE_PROPERTIES = java.util.List.of(de.tum.cit.aet.artemis.core.config.Constants.ATLAS_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.ATLASML_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.ATLASLLM_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.DEIMOS_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.EXAM_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.PLAGIARISM_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.TEXT_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.MODELING_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.FILEUPLOAD_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.LECTURE_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.PASSKEY_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.SHARING_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.THEIA_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.IRIS_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.LTI_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.ATHENA_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.APOLLON_ENABLED_PROPERTY_NAME,
            de.tum.cit.aet.artemis.core.config.Constants.LDAP_ENABLED_PROPERTY_NAME, de.tum.cit.aet.artemis.core.config.Constants.SAML2_ENABLED_PROPERTY_NAME);

    private final ProfileService profiles = mock(ProfileService.class);

    private final NodeRegistryService nodes = mock(NodeRegistryService.class);

    private final LocalCITelemetryApi agents = mock(LocalCITelemetryApi.class);

    private final RestTemplate rest = new RestTemplate();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();

    private final MockEnvironment env = new MockEnvironment();

    private TelemetrySendingService service;

    @BeforeEach
    void setUp() {
        env.setActiveProfiles("prod", "core", "scheduling");
        for (String key : MODULE_PROPERTIES) {
            env.setProperty(key, "false");
        }
        env.setProperty("artemis.iris.enabled", "true");
        service = sender(Optional.of(agents));
    }

    private TelemetrySendingService sender(Optional<LocalCITelemetryApi> api) {
        var sender = new TelemetrySendingService(env, rest, profiles, JsonObjectMapper.get(), nodes, api);
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
        assertThat(JsonObjectMapper.get().writeValueAsString(data)).doesNotContain("adminName", "contact", "numberOfNodes", "buildAgentCount", "isMultiNode");
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
    void distinguishesAnEmptyFeatureListFromAnOlderSenderWithoutFeatureData() {
        env.setProperty("artemis.iris.enabled", "false");
        server.expect(requestTo("https://telemetry.example/api/telemetry"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.moduleFeatures").isArray())
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.moduleFeatures").isEmpty()).andRespond(withSuccess());
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
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() -> service.sendTelemetryByPostRequest(false, "startup-id", Instant.EPOCH));
        server.verify();
    }
}
