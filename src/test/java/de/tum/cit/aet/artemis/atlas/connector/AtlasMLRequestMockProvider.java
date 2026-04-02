package de.tum.cit.aet.artemis.atlas.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasMLEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasMLRestTemplateConfiguration;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;

@Component
@Profile(PROFILE_CORE)
@Conditional(AtlasMLEnabled.class)
@Lazy
public class AtlasMLRequestMockProvider {

    private static final String HEALTHY_ATLASML_RESPONSE = """
            {"status":"ok","components":{"api":{"status":"ok"},"weaviate":{"status":"ok","collections":{"Exercise":{"status":"ok"},"Competency":{"status":"ok"},"SemanticCluster":{"status":"ok"}}}}}
            """;

    private static final String UNHEALTHY_ATLASML_RESPONSE = """
            {"status":"error","components":{"api":{"status":"ok"},"weaviate":{"status":"error","message":"Weaviate is unreachable"}}}
            """;

    private final RestTemplate atlasmlRestTemplate;

    private final RestTemplate shortTimeoutAtlasmlRestTemplate;

    private final AtlasMLRestTemplateConfiguration config;

    private MockRestServiceServer mockServer;

    private MockRestServiceServer shortTimeoutMockServer;

    @Autowired
    private ObjectMapper mapper;

    public AtlasMLRequestMockProvider(@Qualifier("atlasmlRestTemplate") RestTemplate atlasmlRestTemplate,
            @Qualifier("shortTimeoutAtlasmlRestTemplate") RestTemplate shortTimeoutAtlasmlRestTemplate, AtlasMLRestTemplateConfiguration config) {
        this.atlasmlRestTemplate = atlasmlRestTemplate;
        this.shortTimeoutAtlasmlRestTemplate = shortTimeoutAtlasmlRestTemplate;
        this.config = config;
    }

    public void enableMockingOfRequests() {
        if (mockServer == null) {
            mockServer = MockRestServiceServer.bindTo(atlasmlRestTemplate).ignoreExpectOrder(true).bufferContent().build();
        }
        if (shortTimeoutMockServer == null) {
            shortTimeoutMockServer = MockRestServiceServer.bindTo(shortTimeoutAtlasmlRestTemplate).ignoreExpectOrder(true).bufferContent().build();
        }
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
        if (shortTimeoutMockServer != null) {
            shortTimeoutMockServer.reset();
        }
    }

    public void resetAndRebuild() {
        reset();
        // Nullify the servers so they can be rebuilt
        mockServer = null;
        shortTimeoutMockServer = null;
        enableMockingOfRequests();
    }

    public void mockHealth(boolean healthy) {
        var url = URI.create(config.getAtlasmlBaseUrl() + "/api/v1/health/");
        shortTimeoutMockServer.expect(MockRestRequestMatchers.requestTo(url))
                .andRespond(healthy ? MockRestResponseCreators.withSuccess(HEALTHY_ATLASML_RESPONSE, MediaType.APPLICATION_JSON)
                        : MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(UNHEALTHY_ATLASML_RESPONSE));
    }

    public void mockSuggestCompetencies(SuggestCompetencyRequestDTO request, SuggestCompetencyResponseDTO response) throws Exception {
        var url = URI.create(config.getAtlasmlBaseUrl() + "/api/v1/competency/suggest");
        mockServer.expect(MockRestRequestMatchers.requestTo(url)).andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
    }

    public void mockSuggestCompetencyRelations(Long courseId, SuggestCompetencyRelationsResponseDTO response) throws Exception {
        var url = URI.create(config.getAtlasmlBaseUrl() + "/api/v1/competency/relations/suggest/" + courseId);
        mockServer.expect(MockRestRequestMatchers.requestTo(url)).andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
    }

    /**
     * Allows any save call to AtlasML to succeed (used as default to avoid external calls in tests).
     */
    public void mockSaveCompetenciesAny() {
        var url = URI.create(config.getAtlasmlBaseUrl() + "/api/v1/competency/save");
        mockServer.expect(ExpectedCount.manyTimes(), MockRestRequestMatchers.requestTo(url)).andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess("", MediaType.APPLICATION_JSON));
    }

    /**
     * Allows any suggest-competencies call to AtlasML to return an empty list (used as default to avoid external calls in tests).
     */
    public void mockSuggestCompetenciesAny() throws Exception {
        var url = URI.create(config.getAtlasmlBaseUrl() + "/api/v1/competency/suggest");
        mockServer.expect(ExpectedCount.manyTimes(), MockRestRequestMatchers.requestTo(url)).andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(mapper.writeValueAsString(new SuggestCompetencyResponseDTO(List.of())), MediaType.APPLICATION_JSON));
    }

    /**
     * Allows any map-competency-to-exercise call to AtlasML to succeed (used as default to avoid external calls in tests).
     */
    public void mockMapCompetencyToExerciseAny() {
        var url = URI.create(config.getAtlasmlBaseUrl() + "/api/v1/competency/map-competency-to-exercise");
        mockServer.expect(ExpectedCount.manyTimes(), MockRestRequestMatchers.requestTo(url)).andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess("true", MediaType.APPLICATION_JSON));
    }
}
