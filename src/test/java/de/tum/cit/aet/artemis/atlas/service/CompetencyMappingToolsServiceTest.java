package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyRelationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Unit tests for {@link CompetencyMappingToolsService}.
 * Tests the tools provided to the Competency Mapper sub-agent for managing competency relations.
 */
@ExtendWith(MockitoExtension.class)
class CompetencyMappingToolsServiceTest {

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private CompetencyRelationRepository competencyRelationRepository;

    @Mock
    private CompetencyRelationService competencyRelationService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AtlasAgentService atlasAgentService;

    @Mock
    private AtlasMLApi atlasMLApi;

    private ObjectMapper objectMapper;

    private CompetencyMappingToolsService competencyMappingToolsService;

    private Course testCourse;

    private Competency headCompetency;

    private Competency tailCompetency;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        competencyMappingToolsService = new CompetencyMappingToolsService(objectMapper, courseCompetencyRepository, competencyRelationRepository, competencyRelationService,
                courseRepository, atlasAgentService, atlasMLApi);

        testCourse = new Course();
        testCourse.setId(123L);
        testCourse.setTitle("Software Engineering");

        headCompetency = new Competency();
        headCompetency.setId(1L);
        headCompetency.setTitle("OOP Basics");
        headCompetency.setCourse(testCourse);

        tailCompetency = new Competency();
        tailCompetency.setId(2L);
        tailCompetency.setTitle("Design Patterns");
        tailCompetency.setCourse(testCourse);

        AtlasAgentService.resetCompetencyModifiedFlag();
        CompetencyMappingToolsService.clearAllPreviews();
        CompetencyMappingToolsService.clearCurrentSessionId();
    }

    @AfterEach
    void tearDown() {
        CompetencyMappingToolsService.clearAllPreviews();
        CompetencyMappingToolsService.clearCurrentSessionId();
        AtlasAgentService.resetCompetencyModifiedFlag();
    }

    @Nested
    class GetCourseCompetencies {

        @Test
        void shouldReturnAllCompetenciesForValidCourse() throws JsonProcessingException {
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findByCourseIdOrderById(123L)).thenReturn(List.of(headCompetency, tailCompetency));

            String actualResult = competencyMappingToolsService.getCourseCompetencies(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(123L);
            assertThat(actualJsonNode.get("competencies")).isNotNull();
            assertThat(actualJsonNode.get("competencies").isArray()).isTrue();
            assertThat(actualJsonNode.get("competencies").size()).isEqualTo(2);
        }

        @Test
        void shouldReturnEmptyListWhenNoCompetenciesExist() throws JsonProcessingException {
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findByCourseIdOrderById(123L)).thenReturn(List.of());

            String actualResult = competencyMappingToolsService.getCourseCompetencies(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(123L);
            assertThat(actualJsonNode.get("competencies").size()).isZero();
        }

        @Test
        void shouldReturnErrorWhenCourseNotFound() throws JsonProcessingException {
            when(courseRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyMappingToolsService.getCourseCompetencies(999L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Course not found");
        }
    }

    @Nested
    class GetCourseCompetencyRelations {

        @Test
        void shouldReturnAllRelationsForValidCourse() throws JsonProcessingException {
            CompetencyRelation relation = new CompetencyRelation();
            relation.setId(1L);
            relation.setHeadCompetency(headCompetency);
            relation.setTailCompetency(tailCompetency);
            relation.setType(RelationType.ASSUMES);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of(relation));

            String actualResult = competencyMappingToolsService.getCourseCompetencyRelations(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(123L);
            assertThat(actualJsonNode.get("relations")).isNotNull();
            assertThat(actualJsonNode.get("relations").isArray()).isTrue();
            assertThat(actualJsonNode.get("relations").size()).isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyListWhenNoRelationsExist() throws JsonProcessingException {
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());

            String actualResult = competencyMappingToolsService.getCourseCompetencyRelations(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(123L);
            assertThat(actualJsonNode.get("relations").size()).isZero();
        }

        @Test
        void shouldReturnErrorWhenCourseNotFound() throws JsonProcessingException {
            when(courseRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyMappingToolsService.getCourseCompetencyRelations(999L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Course not found");
        }
    }

    @Nested
    class GetLastPreviewedRelation {

        @Test
        void shouldReturnErrorWhenNoActiveSession() throws JsonProcessingException {
            CompetencyMappingToolsService.clearCurrentSessionId();

            String actualResult = competencyMappingToolsService.getLastPreviewedRelation();

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No active session");
        }

        @Test
        void shouldReturnErrorWhenNoCachedRelationData() throws JsonProcessingException {
            String sessionId = "test_session";
            CompetencyMappingToolsService.setCurrentSessionId(sessionId);
            when(atlasAgentService.getCachedRelationData(sessionId)).thenReturn(null);

            String actualResult = competencyMappingToolsService.getLastPreviewedRelation();

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No previewed relation data found");
        }

        @Test
        void shouldReturnCachedRelationDataWhenAvailable() throws JsonProcessingException {
            String sessionId = "test_session";
            CompetencyMappingToolsService.setCurrentSessionId(sessionId);

            List<CompetencyRelationDTO> cachedData = List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES));

            when(atlasAgentService.getCachedRelationData(sessionId)).thenReturn(cachedData);

            String actualResult = competencyMappingToolsService.getLastPreviewedRelation();

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("sessionId").asText()).isEqualTo(sessionId);
            assertThat(actualJsonNode.get("relations")).isNotNull();
            assertThat(actualJsonNode.get("relations").isArray()).isTrue();
            assertThat(actualJsonNode.get("relations").size()).isEqualTo(1);
        }
    }

    @Nested
    class PreviewRelationMappings {

        @Test
        void shouldPreviewSingleRelationSuccessfully() {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            lenient().when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
            lenient().when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), null);

            assertThat(actualResult).contains("Preview generated successfully for 1 relation mapping");

            SingleRelationPreviewResponseDTO preview = CompetencyMappingToolsService.getSingleRelationPreview();
            assertThat(preview).isNotNull();
            assertThat(preview.preview()).isTrue();
            assertThat(preview.relation().headCompetencyId()).isEqualTo(2L);
            assertThat(preview.relation().tailCompetencyId()).isEqualTo(1L);
        }

        @Test
        void shouldPreviewMultipleRelationsSuccessfully() {
            CompetencyRelationDTO relation1 = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);
            CompetencyRelationDTO relation2 = new CompetencyRelationDTO(null, 2L, 1L, RelationType.EXTENDS);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            lenient().when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
            lenient().when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, List.of(relation1, relation2), null);

            assertThat(actualResult).contains("Preview generated successfully for 2 relation mappings");

            BatchRelationPreviewResponseDTO preview = CompetencyMappingToolsService.getBatchRelationPreview();
            assertThat(preview).isNotNull();
            assertThat(preview.batchPreview()).isTrue();
            assertThat(preview.count()).isEqualTo(2);
            assertThat(preview.relations()).hasSize(2);
        }

        @Test
        void shouldReturnErrorWhenNoRelationsProvided() {
            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, List.of(), null);

            assertThat(actualResult).contains("Error: No relations provided for preview");
        }

        @Test
        void shouldReturnErrorWhenRelationsListIsNull() {
            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, null, null);

            assertThat(actualResult).contains("Error: No relations provided for preview");
        }

        @Test
        void shouldReturnErrorWhenCompetencyNotFound() {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 999L, RelationType.ASSUMES);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), null);

            assertThat(actualResult).contains("Error: Competency not found");
        }

        @Test
        void shouldNotCacheRelationsWhenViewOnlyIsTrue() {
            String sessionId = "view_only_session";
            CompetencyMappingToolsService.setCurrentSessionId(sessionId);

            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            lenient().when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
            lenient().when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), true);

            assertThat(actualResult).contains("Preview generated successfully for 1 relation mapping");

            verify(atlasAgentService, never()).cacheRelationOperations(any(), any());
        }

        @Test
        void shouldCacheRelationsWhenViewOnlyIsFalse() {
            String sessionId = "editable_session";
            CompetencyMappingToolsService.setCurrentSessionId(sessionId);

            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            lenient().when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
            lenient().when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            String actualResult = competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), false);

            assertThat(actualResult).contains("Preview generated successfully for 1 relation mapping");

            verify(atlasAgentService).cacheRelationOperations(sessionId, new ArrayList<>(List.of(relation)));
        }

        @Test
        void shouldBuildGraphPreviewWithExistingRelations() {
            CompetencyRelation existingRelation = new CompetencyRelation();
            existingRelation.setId(10L);
            existingRelation.setHeadCompetency(headCompetency);
            existingRelation.setTailCompetency(tailCompetency);
            existingRelation.setType(RelationType.MATCHES);

            CompetencyRelationDTO newRelation = new CompetencyRelationDTO(null, 2L, 1L, RelationType.EXTENDS);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of(existingRelation));
            when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            competencyMappingToolsService.previewRelationMappings(123L, List.of(newRelation), null);

            RelationGraphPreviewDTO graphPreview = CompetencyMappingToolsService.getRelationGraphPreview();
            assertThat(graphPreview).isNotNull();
            assertThat(graphPreview.nodes()).hasSize(2);
            assertThat(graphPreview.edges()).hasSize(2);
        }
    }

    @Nested
    class SuggestRelationMappingsUsingML {

        @Test
        void shouldReturnMLSuggestionsSuccessfully() throws JsonProcessingException {
            List<AtlasMLCompetencyRelationDTO> mlRelations = List.of(new AtlasMLCompetencyRelationDTO(1L, 2L, "ASSUMES"));
            SuggestCompetencyRelationsResponseDTO mlResponse = new SuggestCompetencyRelationsResponseDTO(mlRelations);

            when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(mlResponse);

            String actualResult = competencyMappingToolsService.suggestRelationMappingsUsingML(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("count").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("suggestions")).isNotNull();
            assertThat(actualJsonNode.get("suggestions").isArray()).isTrue();
        }

        @Test
        void shouldReturnErrorWhenMLReturnsEmptyRelations() throws JsonProcessingException {
            SuggestCompetencyRelationsResponseDTO mlResponse = new SuggestCompetencyRelationsResponseDTO(List.of());

            when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(mlResponse);

            String actualResult = competencyMappingToolsService.suggestRelationMappingsUsingML(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No relation suggestions available");
        }

        @Test
        void shouldReturnErrorWhenMLReturnsNull() throws JsonProcessingException {
            when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(null);

            String actualResult = competencyMappingToolsService.suggestRelationMappingsUsingML(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No relation suggestions available");
        }

        @Test
        void shouldSkipInvalidRelationTypes() throws JsonProcessingException {
            List<AtlasMLCompetencyRelationDTO> mlRelations = List.of(new AtlasMLCompetencyRelationDTO(1L, 2L, "ASSUMES"), new AtlasMLCompetencyRelationDTO(2L, 3L, "INVALID_TYPE"));
            SuggestCompetencyRelationsResponseDTO mlResponse = new SuggestCompetencyRelationsResponseDTO(mlRelations);

            when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(mlResponse);

            String actualResult = competencyMappingToolsService.suggestRelationMappingsUsingML(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("count").asInt()).isEqualTo(1);
        }

        @Test
        void shouldReturnErrorWhenAllRelationTypesAreInvalid() throws JsonProcessingException {
            List<AtlasMLCompetencyRelationDTO> mlRelations = List.of(new AtlasMLCompetencyRelationDTO(1L, 2L, "INVALID_TYPE"));
            SuggestCompetencyRelationsResponseDTO mlResponse = new SuggestCompetencyRelationsResponseDTO(mlRelations);

            when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(mlResponse);

            String actualResult = competencyMappingToolsService.suggestRelationMappingsUsingML(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No valid relation suggestions found");
        }

        @Test
        void shouldReturnErrorWhenMLThrowsException() throws JsonProcessingException {
            when(atlasMLApi.suggestCompetencyRelations(123L)).thenThrow(new RuntimeException("ML service error"));

            String actualResult = competencyMappingToolsService.suggestRelationMappingsUsingML(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Failed to get ML-based relation suggestions");
        }
    }

    @Nested
    class SaveRelationMappings {

        @Test
        void shouldCreateNewRelationSuccessfully() throws JsonProcessingException {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(atlasMLApi.mapCompetencyToCompetency(1L, 2L)).thenReturn(true);

            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of(relation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isZero();
            assertThat(actualJsonNode.get("failed").asInt()).isZero();

            verify(competencyRelationService).createCompetencyRelation(headCompetency, tailCompetency, RelationType.ASSUMES, testCourse);
            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();
        }

        @Test
        void shouldUpdateExistingRelationSuccessfully() throws JsonProcessingException {
            CompetencyRelation existingRelation = new CompetencyRelation();
            existingRelation.setId(10L);
            existingRelation.setHeadCompetency(headCompetency);
            existingRelation.setTailCompetency(tailCompetency);
            existingRelation.setType(RelationType.ASSUMES);

            CompetencyRelationDTO updateRelation = new CompetencyRelationDTO(10L, 2L, 1L, RelationType.EXTENDS);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(competencyRelationRepository.findById(10L)).thenReturn(Optional.of(existingRelation));
            when(competencyRelationRepository.save(any(CompetencyRelation.class))).thenReturn(existingRelation);

            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of(updateRelation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isZero();
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("failed").asInt()).isZero();

            ArgumentCaptor<CompetencyRelation> relationCaptor = ArgumentCaptor.forClass(CompetencyRelation.class);
            verify(competencyRelationRepository).save(relationCaptor.capture());
            CompetencyRelation savedRelation = relationCaptor.getValue();
            assertThat(savedRelation.getType()).isEqualTo(RelationType.EXTENDS);
            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();
        }

        @Test
        void shouldHandleBatchOperationsWithMixedCreateAndUpdate() throws JsonProcessingException {
            CompetencyRelation existingRelation = new CompetencyRelation();
            existingRelation.setId(10L);
            existingRelation.setHeadCompetency(headCompetency);
            existingRelation.setTailCompetency(tailCompetency);
            existingRelation.setType(RelationType.ASSUMES);

            CompetencyRelationDTO createRelation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);
            CompetencyRelationDTO updateRelation = new CompetencyRelationDTO(10L, 2L, 1L, RelationType.EXTENDS);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(competencyRelationRepository.findById(10L)).thenReturn(Optional.of(existingRelation));
            when(competencyRelationRepository.save(any(CompetencyRelation.class))).thenReturn(existingRelation);
            when(atlasMLApi.mapCompetencyToCompetency(1L, 2L)).thenReturn(true);

            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of(createRelation, updateRelation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("failed").asInt()).isZero();
        }

        @Test
        void shouldReturnErrorWhenCourseNotFound() throws JsonProcessingException {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyMappingToolsService.saveRelationMappings(999L, List.of(relation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Course not found");

            verify(competencyRelationService, never()).createCompetencyRelation(any(), any(), any(), any());
        }

        @Test
        void shouldReturnErrorWhenNoRelationsProvided() throws JsonProcessingException {
            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of());

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No relations provided");
        }

        @Test
        void shouldReturnErrorWhenRelationsListIsNull() throws JsonProcessingException {
            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No relations provided");
        }

        @Test
        void shouldContinueOnPartialFailuresAndReportErrors() throws JsonProcessingException {
            CompetencyRelationDTO validRelation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);
            CompetencyRelationDTO invalidRelation = new CompetencyRelationDTO(null, 1L, 999L, RelationType.EXTENDS);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(courseCompetencyRepository.findById(999L)).thenReturn(Optional.empty());
            when(atlasMLApi.mapCompetencyToCompetency(1L, 2L)).thenReturn(true);

            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of(validRelation, invalidRelation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isFalse();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("errorDetails")).isNotNull();
            assertThat(actualJsonNode.get("errorDetails").size()).isEqualTo(1);
        }

        @Test
        void shouldReturnErrorWhenRelationToUpdateNotFound() throws JsonProcessingException {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(999L, 1L, 2L, RelationType.ASSUMES);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(competencyRelationRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of(relation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isFalse();
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("errorDetails")).isNotNull();
        }

        @Test
        void shouldHandleAtlasMLSyncFailureGracefully() throws JsonProcessingException {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(atlasMLApi.mapCompetencyToCompetency(1L, 2L)).thenThrow(new RuntimeException("ML sync error"));

            String actualResult = competencyMappingToolsService.saveRelationMappings(123L, List.of(relation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);

            verify(competencyRelationService).createCompetencyRelation(headCompetency, tailCompetency, RelationType.ASSUMES, testCourse);
        }
    }

    @Nested
    class ThreadLocalManagement {

        @Test
        void shouldSetAndClearSessionId() {
            String testSessionId = "test_session_123";

            CompetencyMappingToolsService.setCurrentSessionId(testSessionId);
            CompetencyMappingToolsService.clearCurrentSessionId();

            String result = competencyMappingToolsService.getLastPreviewedRelation();
            assertThat(result).contains("No active session");
        }

        @Test
        void shouldClearAllPreviewsSuccessfully() {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            lenient().when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
            lenient().when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), null);

            assertThat(CompetencyMappingToolsService.getSingleRelationPreview()).isNotNull();

            CompetencyMappingToolsService.clearAllPreviews();

            assertThat(CompetencyMappingToolsService.getSingleRelationPreview()).isNull();
            assertThat(CompetencyMappingToolsService.getBatchRelationPreview()).isNull();
            assertThat(CompetencyMappingToolsService.getRelationGraphPreview()).isNull();
        }
    }

    @Nested
    class RelationTypeMapping {

        @Test
        void shouldMapAllRelationTypesCorrectly() {
            for (RelationType relationType : RelationType.values()) {
                CompetencyMappingToolsService.clearAllPreviews();

                CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, relationType);

                when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
                when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
                lenient().when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
                lenient().when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

                String result = competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), null);

                assertThat(result).contains("Preview generated successfully for 1 relation mapping");

                SingleRelationPreviewResponseDTO preview = CompetencyMappingToolsService.getSingleRelationPreview();
                assertThat(preview).isNotNull();
                assertThat(preview.relation().relationType()).isEqualTo(relationType);
            }
        }
    }

    @Nested
    class GraphPreviewGeneration {

        @Test
        void shouldExcludeUpdatedRelationsFromExistingRelations() {
            CompetencyRelation existingRelation = new CompetencyRelation();
            existingRelation.setId(10L);
            existingRelation.setHeadCompetency(headCompetency);
            existingRelation.setTailCompetency(tailCompetency);
            existingRelation.setType(RelationType.ASSUMES);

            CompetencyRelationDTO updateRelation = new CompetencyRelationDTO(10L, 1L, 2L, RelationType.EXTENDS);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of(existingRelation));
            when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            competencyMappingToolsService.previewRelationMappings(123L, List.of(updateRelation), null);

            RelationGraphPreviewDTO graphPreview = CompetencyMappingToolsService.getRelationGraphPreview();
            assertThat(graphPreview).isNotNull();
            assertThat(graphPreview.edges()).hasSize(1);
            assertThat(graphPreview.edges().getFirst().id()).isEqualTo("edge-10");
            assertThat(graphPreview.edges().getFirst().relationType()).isEqualTo(RelationType.EXTENDS);
        }

        @Test
        void shouldHandleViewOnlyFlagInGraphPreview() {
            CompetencyRelationDTO relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);

            when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(headCompetency));
            when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tailCompetency));
            when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());
            when(courseCompetencyRepository.findAllById(any())).thenReturn(List.of(headCompetency, tailCompetency));

            competencyMappingToolsService.previewRelationMappings(123L, List.of(relation), true);

            RelationGraphPreviewDTO graphPreview = CompetencyMappingToolsService.getRelationGraphPreview();
            assertThat(graphPreview).isNotNull();
            assertThat(graphPreview.viewOnly()).isTrue();
        }
    }
}
