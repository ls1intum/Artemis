package de.tum.cit.aet.artemis.atlas.service.atlasml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.config.AtlasMLRestTemplateConfiguration;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AtlasMLServiceTest {

    @Mock
    private RestTemplate atlasmlRestTemplate;

    @Mock
    private RestTemplate shortTimeoutAtlasmlRestTemplate;

    @Mock
    private AtlasMLRestTemplateConfiguration config;

    @Mock
    private CompetencyRepository competencyRepository;

    @Mock
    private CompetencyExerciseLinkTestRepository competencyExerciseLinkRepository;

    @Mock
    private FeatureToggleService featureToggleService;

    private AtlasMLService atlasMLService;

    @BeforeEach
    void setUp() {
        when(config.getAtlasmlBaseUrl()).thenReturn("http://localhost:8000");
        when(featureToggleService.isFeatureEnabled(Feature.AtlasML)).thenReturn(true);
        atlasMLService = new AtlasMLService(atlasmlRestTemplate, shortTimeoutAtlasmlRestTemplate, config, competencyRepository, competencyExerciseLinkRepository,
                featureToggleService);
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
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description", 1L);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(
                        "{\"competencies\":[{\"id\":1,\"title\":\"Test Competency 1\",\"description\":\"Test Description 1\",\"course_id\":1},{\"id\":2,\"title\":\"Test Competency 2\",\"description\":\"Test Description 2\",\"course_id\":1}]}",
                        HttpStatus.OK));

        // When
        SuggestCompetencyResponseDTO result = atlasMLService.suggestCompetencies(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.competencies()).hasSize(2);
        assertThat(result.competencies().get(0).id()).isEqualTo(1L);
        assertThat(result.competencies().get(1).id()).isEqualTo(2L);
    }

    @Test
    void testSaveCompetencies() {
        // Given
        AtlasMLCompetencyDTO competencyDTO = new AtlasMLCompetencyDTO(1L, "Test Competency", "Test Description", 1L);

        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO(List.of(competencyDTO), null, OperationTypeDTO.UPDATE.value());

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

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

        SaveCompetencyRequestDTO request = SaveCompetencyRequestDTO.fromCompetency(competency, OperationTypeDTO.UPDATE);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        atlasMLService.saveCompetencies(request);

        // Then - no exception should be thrown
        // The method is void, so we just verify it completes successfully
    }

    @Test
    void testSaveCompetencies_WhenServiceThrowsException() {
        // Given
        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO(null, null, OperationTypeDTO.UPDATE.value());

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.saveCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencies_WhenServiceThrowsException() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description", 1L);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencies_EmptyResponse() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description", 1L);
        ResponseEntity<String> response = new ResponseEntity<>("[]", HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // When
        SuggestCompetencyResponseDTO result = atlasMLService.suggestCompetencies(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.competencies()).isEmpty();
    }

    @Test
    void testSuggestCompetencies_InvalidJsonResponse() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description", 1L);
        ResponseEntity<String> response = new ResponseEntity<>("invalid json", HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencyRelations() {
        // Given
        Long courseId = 123L;
        // SuggestCompetencyRelationsResponseDTO expectedResponse = new SuggestCompetencyRelationsResponseDTO(List.of());
        String responseJson = "{\"relations\":[]}";
        ResponseEntity<String> response = new ResponseEntity<>(responseJson, HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/relations/suggest/123"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // When
        SuggestCompetencyRelationsResponseDTO result = atlasMLService.suggestCompetencyRelations(courseId);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void testSuggestCompetencyRelations_WhenServiceThrowsException() {
        // Given
        Long courseId = 123L;

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/relations/suggest/123"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencyRelations(courseId);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSaveExercise_WithAllParameters() {
        // Given
        Long exerciseId = 123L;
        String title = "Test Exercise";
        String description = "Test Description";
        List<Long> competencyIds = List.of(1L, 2L);
        Long courseId = 123L;

        ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveExercise(exerciseId, title, description, competencyIds, courseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExercise_DefaultOperationTypeDTO() {
        // Given
        Long exerciseId = 123L;
        String title = "Test Exercise";
        String description = "Test Description";
        List<Long> competencyIds = List.of(1L, 2L);
        Long courseId = 123L;

        ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveExercise(exerciseId, title, description, competencyIds, courseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExercise_WhenFeatureDisabled() {
        // Given
        Long exerciseId = 123L;
        when(featureToggleService.isFeatureEnabled(Feature.AtlasML)).thenReturn(false);

        // When
        boolean result = atlasMLService.saveExercise(123L, "title", "desc", List.of(), 123L, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExercise_WhenException() {
        // Given
        Long exerciseId = 123L;
        String title = "Test Exercise";
        String description = "Test Description";
        List<Long> competencyIds = List.of(1L, 2L);
        Long courseId = 123L;

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        boolean result = atlasMLService.saveExercise(exerciseId, title, description, competencyIds, courseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testSaveExerciseWithCompetencies() {
        // Given
        Exercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setTitle("Test Exercise");
        exercise.setProblemStatement("Test problem statement");

        Course course = new Course();
        course.setId(1L);
        exercise.setCourse(course);

        Competency competency = new Competency();
        competency.setId(1L);

        CompetencyExerciseLink link = new CompetencyExerciseLink();
        link.setExercise(exercise);
        link.setCompetency(competency);

        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(1L)).thenReturn(List.of(link));

        ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK);
        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExerciseWithCompetencies_WhenFeatureDisabled() {
        // Given
        Exercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasML)).thenReturn(false);

        // When
        boolean result = atlasMLService.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExerciseWithCompetencies_WithNullDescription() {
        // Given
        Exercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setTitle("Test Exercise");
        exercise.setProblemStatement(null); // null problem statement

        Course course = new Course();
        course.setId(1L);
        exercise.setCourse(course);

        Competency competency = new Competency();
        competency.setId(1L);

        CompetencyExerciseLink link = new CompetencyExerciseLink();
        link.setExercise(exercise);
        link.setCompetency(competency);

        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(1L)).thenReturn(List.of(link));

        ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK);
        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExerciseWithCompetencies_WhenException() {
        // Given
        Exercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setTitle("Test Exercise");
        exercise.setProblemStatement("Test problem statement");

        Course course = new Course();
        course.setId(1L);
        exercise.setCourse(course);

        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(1L)).thenThrow(new RuntimeException("Database error"));

        // When
        boolean result = atlasMLService.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testSaveExerciseWithCompetenciesById() {
        // Given
        Long exerciseId = 1L;
        Exercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Test Exercise");
        exercise.setProblemStatement("Test problem statement");

        Course course = new Course();
        course.setId(1L);
        exercise.setCourse(course);

        Competency competency = new Competency();
        competency.setId(1L);

        CompetencyExerciseLink link = new CompetencyExerciseLink();
        link.setExercise(exercise);
        link.setCompetency(competency);

        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId)).thenReturn(List.of(link));

        ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK);
        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response);

        // When
        boolean result = atlasMLService.saveExerciseWithCompetenciesById(exerciseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExerciseWithCompetenciesById_NoLinks() {
        // Given
        Long exerciseId = 1L;
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId)).thenReturn(List.of());

        // When
        boolean result = atlasMLService.saveExerciseWithCompetenciesById(exerciseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExerciseWithCompetenciesById_WhenFeatureDisabled() {
        // Given
        Long exerciseId = 1L;
        when(featureToggleService.isFeatureEnabled(Feature.AtlasML)).thenReturn(false);

        // When
        boolean result = atlasMLService.saveExerciseWithCompetenciesById(exerciseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testSaveExerciseWithCompetenciesById_WhenException() {
        // Given
        Long exerciseId = 1L;
        when(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId)).thenThrow(new RuntimeException("Database error"));

        // When
        boolean result = atlasMLService.saveExerciseWithCompetenciesById(exerciseId, OperationTypeDTO.UPDATE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsHealthy_HttpServerErrorException() {
        // Given
        when(shortTimeoutAtlasmlRestTemplate.getForEntity(eq("http://localhost:8000/api/v1/health/"), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        boolean result = atlasMLService.isHealthy();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsHealthy_ResourceAccessException() {
        // Given
        when(shortTimeoutAtlasmlRestTemplate.getForEntity(eq("http://localhost:8000/api/v1/health/"), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When
        boolean result = atlasMLService.isHealthy();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsHealthy_GenericException() {
        // Given
        when(shortTimeoutAtlasmlRestTemplate.getForEntity(eq("http://localhost:8000/api/v1/health/"), eq(String.class))).thenThrow(new RuntimeException("Unexpected error"));

        // When
        boolean result = atlasMLService.isHealthy();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testSaveCompetencies_WhenFeatureDisabled() {
        // Given
        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO(null, null, OperationTypeDTO.UPDATE.value());
        when(featureToggleService.isFeatureEnabled(Feature.AtlasML)).thenReturn(false);

        // When
        atlasMLService.saveCompetencies(request);

        // Then - should not make HTTP call
        verify(atlasmlRestTemplate, org.mockito.Mockito.never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testSaveCompetencies_HttpServerErrorException() {
        // Given
        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO(null, null, OperationTypeDTO.UPDATE.value());

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.saveCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSaveCompetencies_ResourceAccessException() {
        // Given
        SaveCompetencyRequestDTO request = new SaveCompetencyRequestDTO(null, null, OperationTypeDTO.UPDATE.value());

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/save"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.saveCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencies_HttpServerErrorException() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description", 1L);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencies_ResourceAccessException() {
        // Given
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO("test description", 1L);

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/suggest"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencies(request);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencyRelations_HttpServerErrorException() {
        // Given
        Long courseId = 123L;

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/relations/suggest/123"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencyRelations(courseId);
        }).isInstanceOf(AtlasMLServiceException.class);
    }

    @Test
    void testSuggestCompetencyRelations_ResourceAccessException() {
        // Given
        Long courseId = 123L;

        when(atlasmlRestTemplate.exchange(eq("http://localhost:8000/api/v1/competency/relations/suggest/123"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> {
            atlasMLService.suggestCompetencyRelations(courseId);
        }).isInstanceOf(AtlasMLServiceException.class);
    }
}
