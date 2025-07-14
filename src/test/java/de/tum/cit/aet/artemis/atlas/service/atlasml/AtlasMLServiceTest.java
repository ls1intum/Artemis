package de.tum.cit.aet.artemis.atlas.service.atlasml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.config.AtlasMLRestTemplateConfiguration;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;

@ExtendWith(MockitoExtension.class)
class AtlasMLServiceTest {

    @Mock
    private RestTemplate atlasmlRestTemplate;

    @Mock
    private RestTemplate shortTimeoutAtlasmlRestTemplate;

    @Mock
    private AtlasMLRestTemplateConfiguration config;

    @Mock
    private CompetencyRepository competencyRepository;

    private AtlasMLService atlasMLService;

    @BeforeEach
    void setUp() {
        when(config.getAtlasmlBaseUrl()).thenReturn("http://localhost:8000");
        atlasMLService = new AtlasMLService(atlasmlRestTemplate, shortTimeoutAtlasmlRestTemplate, config, competencyRepository);
    }

    @Test
    void testIsHealthy_WhenServiceIsUp() {
        // Given
        ResponseEntity<String> response = new ResponseEntity<>("[]", HttpStatus.OK);
        when(shortTimeoutAtlasmlRestTemplate.getForEntity(eq("http://localhost:8000/api/v1/health/"), eq(String.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.isHealthy();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsHealthy_WhenServiceIsDown() {
        // Given
        when(shortTimeoutAtlasmlRestTemplate.getForEntity(eq("http://localhost:8000/api/v1/health/"), eq(String.class))).thenThrow(new RuntimeException("Connection failed"));

        // When
        boolean result = atlasMLService.isHealthy();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testSuggestCompetencies() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description");

        List<String> competencyIds = List.of("comp-001", "comp-002");
        AtlasMLCompetencyRelationDTO relation = new AtlasMLCompetencyRelationDTO("comp-001", "comp-002", "ASSUMES");

        SuggestCompetencyResponseDTO expectedResponse = new SuggestCompetencyResponseDTO(competencyIds, List.of(relation));

        ResponseEntity<SuggestCompetencyResponseDTO> response = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(SuggestCompetencyResponseDTO.class))).thenReturn(response);

        // When
        SuggestCompetencyResponseDTO result = atlasMLService.suggestCompetencies(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.competencies()).hasSize(2);
        assertThat(result.competencyRelations()).hasSize(1);
        assertThat(result.competencies()).contains("comp-001", "comp-002");
        assertThat(result.competencyRelations().get(0).relationType()).isEqualTo("ASSUMES");
    }

    @Test
    void testSaveCompetencies() {
        // Given
        AtlasMLCompetencyDTO competencyDTO = new AtlasMLCompetencyDTO("comp-001", "Test Competency", "Test Description", "APPLY");
        AtlasMLCompetencyRelationDTO relation = new AtlasMLCompetencyRelationDTO("comp-001", "comp-002", "ASSUMES");

        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO("test-id", "test description", List.of(competencyDTO), List.of(relation));

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class))).thenReturn(response);

        // When
        atlasMLService.saveCompetencies(request);

        // Then - no exception should be thrown
        // The method is void, so we just verify it completes successfully
    }

    @Test
    void testSaveCompetencies_WithDomainObjects() {
        // Given
        Competency competency = new Competency();
        competency.setId(1L);
        competency.setTitle("Test Competency");
        competency.setDescription("Test Description");
        competency.setTaxonomy(CompetencyTaxonomy.APPLY);
        competency.setOptional(false);

        CompetencyRelation relation = new CompetencyRelation();
        relation.setType(RelationType.ASSUMES);

        SaveCompetencyRequestDTO request = SaveCompetencyRequestDTO.fromDomain("test-id", "test description", List.of(competency), List.of(relation));

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class))).thenReturn(response);

        // When
        atlasMLService.saveCompetencies(request);

        // Then - no exception should be thrown
        // The method is void, so we just verify it completes successfully
    }

    @Test
    void testSaveCompetencies_WhenServiceThrowsException() {
        // Given
        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO("test-id", "test description", List.of(), List.of());

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.saveCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencies_WhenServiceThrowsException() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description");

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(SuggestCompetencyResponseDTO.class))).thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }
}
