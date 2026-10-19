package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyRelationService;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;

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
    private CourseTestRepository courseTestRepository;

    @Mock
    private AtlasAgentSessionCacheService sessionCacheService;

    @Mock
    private AtlasMLApi atlasMLApi;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    @Mock
    private UserTestRepository userRepository;

    private CompetencyMappingToolsService service;

    private JsonMapper objectMapper;

    private Course course;

    private Competency head;

    private Competency tail;

    @BeforeEach
    void setUp() {
        objectMapper = JsonObjectMapper.get();
        service = new CompetencyMappingToolsService(objectMapper, courseCompetencyRepository, competencyRelationRepository, competencyRelationService, courseTestRepository,
                sessionCacheService, Optional.of(atlasMLApi), authorizationCheckService, userRepository);

        course = new Course();
        course.setId(123L);

        head = new Competency();
        head.setId(1L);
        head.setTitle("Head");
        head.setCourse(course);

        tail = new Competency();
        tail.setId(2L);
        tail.setTitle("Tail");
        tail.setCourse(course);

        AtlasAgentService.resetCompetencyModifiedFlag();
        CompetencyMappingToolsService.clearAllPreviews();
        CompetencyMappingToolsService.clearCurrentSessionId();
    }

    @Test
    void getCourseCompetencies_returnsCompetencies() throws Exception {
        when(courseTestRepository.findById(123L)).thenReturn(Optional.of(course));
        when(courseCompetencyRepository.findByCourseIdOrderById(123L)).thenReturn(List.of(head, tail));

        JsonNode json = objectMapper.readTree(service.getCourseCompetencies(123L));

        assertThat(json.get("courseId").asLong()).isEqualTo(123L);
        assertThat(json.get("competencies")).hasSize(2);
    }

    @Test
    void getCourseCompetencies_courseNotFound() throws Exception {
        when(courseTestRepository.findById(999L)).thenReturn(Optional.empty());

        JsonNode json = objectMapper.readTree(service.getCourseCompetencies(999L));

        assertThat(json.get("error").asString()).contains("Course not found");
    }

    @Test
    void preview_singleRelation_createsSinglePreview() {
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        when(courseCompetencyRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(head, tail));
        when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());

        String result = service.previewRelationMappings(123L, List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES)), false);

        assertThat(result).contains("Preview generated successfully");
        assertThat(CompetencyMappingToolsService.getSingleRelationPreview()).isNotNull();
    }

    @Test
    void preview_viewOnly_doesNotCache() {
        CompetencyMappingToolsService.setCurrentSessionId("session");

        when(courseCompetencyRepository.findById(anyLong())).thenReturn(Optional.of(head), Optional.of(tail));
        when(courseCompetencyRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(head, tail));
        when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of());

        service.previewRelationMappings(123L, List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES)), true);

        verify(sessionCacheService, never()).cacheRelationOperations(123L + "", List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES)));
    }

    @Test
    void saveRelationMappings_createsRelation() throws Exception {
        when(courseTestRepository.findById(123L)).thenReturn(Optional.of(course));
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));

        JsonNode json = objectMapper.readTree(service.saveRelationMappings(123L, List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES))));

        assertThat(json.get("created").asInt()).isEqualTo(1);
        verify(competencyRelationService).createCompetencyRelation(head, tail, RelationType.ASSUMES, course);
    }

    @Test
    void saveRelationMappings_partialFailure_isReported() throws Exception {
        when(courseTestRepository.findById(123L)).thenReturn(Optional.of(course));
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        when(courseCompetencyRepository.findById(999L)).thenReturn(Optional.empty());

        JsonNode json = objectMapper.readTree(service.saveRelationMappings(123L,
                List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES), new CompetencyRelationDTO(null, 1L, 999L, RelationType.EXTENDS))));

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("failed").asInt()).isEqualTo(1);
    }

    @Test
    void suggestRelations_happyPath() throws Exception {
        when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(new SuggestCompetencyRelationsResponseDTO(List.of(new AtlasMLCompetencyRelationDTO(1L, 2L, "ASSUMES"))));

        JsonNode json = objectMapper.readTree(service.suggestRelationMappingsUsingML(123L));

        assertThat(json.get("count").asInt()).isEqualTo(1);
    }

    @Test
    void suggestRelations_mlFailure_returnsError() throws Exception {
        when(atlasMLApi.suggestCompetencyRelations(123L)).thenThrow(new RuntimeException("boom"));

        JsonNode json = objectMapper.readTree(service.suggestRelationMappingsUsingML(123L));

        assertThat(json.get("error").asString()).contains("Failed to get ML-based");
    }

    @Test
    void lastPreviewRequiresActiveSessionAndNonemptyCachedOperations() {
        assertThat(service.getLastPreviewedRelation()).contains("No active session");
        CompetencyMappingToolsService.setCurrentSessionId("refinement");
        assertThat(service.getLastPreviewedRelation()).contains("No previewed relation data");
        when(sessionCacheService.getCachedRelationData("refinement")).thenReturn(List.of());
        assertThat(service.getLastPreviewedRelation()).contains("No previewed relation data");
        var relation = new CompetencyRelationDTO(9L, 1L, 2L, RelationType.EXTENDS);
        when(sessionCacheService.getCachedRelationData("refinement")).thenReturn(List.of(relation));
        assertThat(objectMapper.readTree(service.getLastPreviewedRelation()).get("relations").get(0).get("id").asLong()).isEqualTo(9L);
    }

    @Test
    void rejectsEmptyPreviewsAndSavesBeforeRepositoryAccess() {
        assertThat(service.previewRelationMappings(123L, null, null)).isEqualTo("Error: No relations provided for preview.");
        assertThat(service.previewRelationMappings(123L, List.of(), true)).isEqualTo("Error: No relations provided for preview.");
        assertThat(service.saveRelationMappings(123L, null)).contains("No relations provided");
        assertThat(service.saveRelationMappings(123L, List.of())).contains("No relations provided");
        org.mockito.Mockito.verifyNoInteractions(courseCompetencyRepository, competencyRelationService, courseTestRepository);
    }

    @Test
    void previewRejectsMissingAndForeignCompetencies() {
        var relation = new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES);
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.empty());
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        assertThat(service.previewRelationMappings(123L, List.of(relation), false)).contains("Competency not found");
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        assertThat(service.previewRelationMappings(999L, List.of(relation), false)).contains("Competency not found");
        Course foreignCourse = new Course();
        foreignCourse.setId(999L);
        tail.setCourse(foreignCourse);
        assertThat(service.previewRelationMappings(123L, List.of(relation), false)).contains("Competency not found");
        assertThat(CompetencyMappingToolsService.getSingleRelationPreview()).isNull();
    }

    @Test
    void batchPreviewReplacesUpdatedGraphEdgeAndCachesEditableOperations() {
        CompetencyMappingToolsService.setCurrentSessionId("edit");
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        when(courseCompetencyRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(head, tail));
        var existing = new de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation();
        existing.setId(10L);
        existing.setHeadCompetency(head);
        existing.setTailCompetency(tail);
        existing.setType(RelationType.ASSUMES);
        var unchanged = new de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation();
        unchanged.setId(20L);
        unchanged.setHeadCompetency(tail);
        unchanged.setTailCompetency(head);
        unchanged.setType(RelationType.ASSUMES);
        when(competencyRelationRepository.findAllWithHeadAndTailByCourseId(123L)).thenReturn(Set.of(existing, unchanged));
        var relations = List.of(new CompetencyRelationDTO(10L, 1L, 2L, RelationType.EXTENDS), new CompetencyRelationDTO(null, 2L, 1L, RelationType.ASSUMES));
        assertThat(service.previewRelationMappings(123L, relations, null)).contains("2 relation mappings");
        assertThat(CompetencyMappingToolsService.getSingleRelationPreview()).isNull();
        assertThat(CompetencyMappingToolsService.getBatchRelationPreview().count()).isEqualTo(2);
        var graph = CompetencyMappingToolsService.getRelationGraphPreview();
        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.edges()).hasSize(3);
        assertThat(graph.edges()).filteredOn(edge -> edge.id().equals("edge-10")).extracting(edge -> edge.relationType()).containsExactly(RelationType.EXTENDS);
        verify(sessionCacheService).cacheRelationOperations("edit", relations);
    }

    @Test
    void saveRejectsSelfReferenceMissingRelationAndMissingCourse() {
        var relation = new CompetencyRelationDTO(99L, 1L, 2L, RelationType.ASSUMES);
        assertThat(service.saveRelationMappings(123L, List.of(relation))).contains("Course not found");
        when(courseTestRepository.findById(123L)).thenReturn(Optional.of(course));
        assertThat(service.saveRelationMappings(123L, List.of(new CompetencyRelationDTO(null, 1L, 1L, RelationType.ASSUMES)))).contains("self-referential");
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        assertThat(service.saveRelationMappings(123L, List.of(relation))).contains("Relation not found with ID: 99");
        verify(competencyRelationService, never()).createCompetencyRelation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveZeroIdCreatesAndAtlasFailureDoesNotUndoSavedRelation() {
        when(courseTestRepository.findById(123L)).thenReturn(Optional.of(course));
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        when(atlasMLApi.mapCompetencyToCompetency(2L, 1L)).thenThrow(new IllegalStateException("offline"));
        var result = objectMapper.readTree(service.saveRelationMappings(123L, List.of(new CompetencyRelationDTO(0L, 1L, 2L, RelationType.ASSUMES))));
        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("created").asInt()).isEqualTo(1);
        assertThat(result.get("message").asString()).isEqualTo("1 relation created");
        verify(competencyRelationService).createCompetencyRelation(head, tail, RelationType.ASSUMES, course);
    }

    @Test
    void updateFailureRestoresOriginalRelationAndReportsFailure() {
        when(courseTestRepository.findById(123L)).thenReturn(Optional.of(course));
        when(courseCompetencyRepository.findById(1L)).thenReturn(Optional.of(head));
        when(courseCompetencyRepository.findById(2L)).thenReturn(Optional.of(tail));
        var existing = new de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation();
        existing.setId(9L);
        existing.setHeadCompetency(head);
        existing.setTailCompetency(tail);
        existing.setType(RelationType.ASSUMES);
        when(competencyRelationRepository.findById(9L)).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("cycle")).when(competencyRelationService).createCompetencyRelation(head, tail, RelationType.EXTENDS, course);
        var result = objectMapper.readTree(service.saveRelationMappings(123L, List.of(new CompetencyRelationDTO(9L, 1L, 2L, RelationType.EXTENDS))));
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("updated").asInt()).isZero();
        assertThat(result.get("errorDetails").get(0).asString()).isEqualTo("Failed to update relation");
        verify(competencyRelationRepository).delete(existing);
        verify(competencyRelationService).createCompetencyRelation(tail, head, RelationType.ASSUMES, course);
    }

    @Test
    void suggestionsRejectAbsentEmptyAndInvalidResults() {
        service = new CompetencyMappingToolsService(objectMapper, courseCompetencyRepository, competencyRelationRepository, competencyRelationService, courseTestRepository,
                sessionCacheService, Optional.empty(), authorizationCheckService, userRepository);
        assertThat(service.suggestRelationMappingsUsingML(123L)).contains("AtlasML is not available");
        service = new CompetencyMappingToolsService(objectMapper, courseCompetencyRepository, competencyRelationRepository, competencyRelationService, courseTestRepository,
                sessionCacheService, Optional.of(atlasMLApi), authorizationCheckService, userRepository);
        assertThat(service.suggestRelationMappingsUsingML(123L)).contains("No relation suggestions");
        when(atlasMLApi.suggestCompetencyRelations(123L)).thenReturn(new SuggestCompetencyRelationsResponseDTO(null), new SuggestCompetencyRelationsResponseDTO(List.of()),
                new SuggestCompetencyRelationsResponseDTO(List.of(new AtlasMLCompetencyRelationDTO(1L, 2L, "INVALID"))));
        assertThat(service.suggestRelationMappingsUsingML(123L)).contains("No relation suggestions");
        assertThat(service.suggestRelationMappingsUsingML(123L)).contains("No relation suggestions");
        assertThat(service.suggestRelationMappingsUsingML(123L)).contains("No valid relation suggestions");
    }
}
