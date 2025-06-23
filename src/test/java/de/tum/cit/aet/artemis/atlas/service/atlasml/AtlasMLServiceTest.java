package de.tum.cit.aet.artemis.atlas.service.atlasml;

import static org.assertj.core.api.Assertions.assertThat;
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
import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService.CompetencySuggestionResult;

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

    private AtlasMLServiceImpl atlasMLService;

    @BeforeEach
    void setUp() {
        when(config.getAtlasmlBaseUrl()).thenReturn("http://localhost:8000");
        atlasMLService = new AtlasMLServiceImpl(atlasmlRestTemplate, shortTimeoutAtlasmlRestTemplate, config, competencyRepository);
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
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test-id", "test description");

        List<String> competencyIds = List.of("comp-001", "comp-002");
        AtlasMLCompetencyRelationDTO relationDTO = new AtlasMLCompetencyRelationDTO("tail-id", "head-id", "SUPERSET");

        SuggestCompetencyResponseDTO expectedResponse = new SuggestCompetencyResponseDTO(competencyIds, List.of(relationDTO));

        ResponseEntity<SuggestCompetencyResponseDTO> response = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(SuggestCompetencyResponseDTO.class))).thenReturn(response);

        // When
        SuggestCompetencyResponseDTO result = atlasMLService.suggestCompetencies(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompetencies()).hasSize(2);
        assertThat(result.getCompetencyRelations()).hasSize(1);
        assertThat(result.getCompetencies()).contains("comp-001", "comp-002");
        assertThat(result.getCompetencyRelations().get(0).getRelationType()).isEqualTo("SUPERSET");
    }

    @Test
    void testSaveCompetencies() {
        // Given
        AtlasMLCompetencyDTO competencyDTO = new AtlasMLCompetencyDTO("Test Competency", "Test Description", "APPLY");
        AtlasMLCompetencyRelationDTO relationDTO = new AtlasMLCompetencyRelationDTO("tail-id", "head-id", "SUBSET");

        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO("test-id", "test description", List.of(competencyDTO), List.of(relationDTO));

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveCompetencies(request);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSuggestCompetenciesAsDomain() {
        // Given
        List<String> competencyIds = List.of("1", "2");
        AtlasMLCompetencyRelationDTO relationDTO = new AtlasMLCompetencyRelationDTO("tail-id", "head-id", "SUPERSET");

        SuggestCompetencyResponseDTO expectedResponse = new SuggestCompetencyResponseDTO(competencyIds, List.of(relationDTO));

        ResponseEntity<SuggestCompetencyResponseDTO> response = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(SuggestCompetencyResponseDTO.class))).thenReturn(response);

        Competency competency1 = new Competency();
        competency1.setTitle("Competency 1");
        Competency competency2 = new Competency();
        competency2.setTitle("Competency 2");
        List<Competency> mockedCompetencies = List.of(competency1, competency2);

        when(competencyRepository.findAllById(List.of(1L, 2L))).thenReturn(mockedCompetencies);

        // When
        List<Competency> result = atlasMLService.suggestCompetenciesAsDomain("test-id", "test description");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrderElementsOf(mockedCompetencies);
        assertThat(result.get(0).getTitle()).isEqualTo("Competency 1");
        assertThat(result.get(1).getTitle()).isEqualTo("Competency 2");
    }

    @Test
    void testSuggestCompetencyIds() {
        // Given
        List<String> competencyIds = List.of("comp-001", "comp-002");
        AtlasMLCompetencyRelationDTO relationDTO = new AtlasMLCompetencyRelationDTO("tail-id", "head-id", "SUPERSET");

        SuggestCompetencyResponseDTO expectedResponse = new SuggestCompetencyResponseDTO(competencyIds, List.of(relationDTO));

        ResponseEntity<SuggestCompetencyResponseDTO> response = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(SuggestCompetencyResponseDTO.class))).thenReturn(response);

        // When
        List<String> result = atlasMLService.suggestCompetencyIds("test-id", "test description");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).contains("comp-001", "comp-002");
    }

    @Test
    void testSaveCompetenciesWithDomainObjects() {
        // Given
        Competency competency = new Competency();
        competency.setTitle("Test Competency");
        competency.setDescription("Test Description");
        competency.setTaxonomy(CompetencyTaxonomy.APPLY);
        competency.setOptional(false);

        CompetencyRelation relation = new CompetencyRelation();
        relation.setType(RelationType.EXTENDS);

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveCompetencies("test-id", "test description", List.of(competency), List.of(relation));

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveCompetenciesFailure() {
        // Given
        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO();

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        boolean result = atlasMLService.saveCompetencies(request);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testSuggestCompetenciesWithRelations() {
        // Given
        List<String> competencyIds = List.of("1", "2");
        AtlasMLCompetencyRelationDTO relationDTO = new AtlasMLCompetencyRelationDTO("1", "2", "SUPERSET");

        SuggestCompetencyResponseDTO expectedResponse = new SuggestCompetencyResponseDTO(competencyIds, List.of(relationDTO));

        ResponseEntity<SuggestCompetencyResponseDTO> response = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(SuggestCompetencyResponseDTO.class))).thenReturn(response);

        Competency competency1 = new Competency();
        competency1.setId(1L);
        competency1.setTitle("Competency 1");
        Competency competency2 = new Competency();
        competency2.setId(2L);
        competency2.setTitle("Competency 2");
        List<Competency> mockedCompetencies = List.of(competency1, competency2);

        when(competencyRepository.findAllById(List.of(1L, 2L))).thenReturn(mockedCompetencies);

        // When
        CompetencySuggestionResult result = atlasMLService.suggestCompetenciesWithRelations("test-id", "test description");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompetencies()).hasSize(2);
        assertThat(result.getCompetencies()).containsExactlyInAnyOrder(competency1, competency2);
        assertThat(result.getRelations()).hasSize(1);
        assertThat(result.getRelations().get(0).getType()).isEqualTo(RelationType.ASSUMES);
    }
}
