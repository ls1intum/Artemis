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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyRelationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;

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

    private CompetencyMappingToolsService service;

    private ObjectMapper objectMapper;

    private Course course;

    private Competency head;

    private Competency tail;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new CompetencyMappingToolsService(objectMapper, courseCompetencyRepository, competencyRelationRepository, competencyRelationService, courseTestRepository,
                sessionCacheService, atlasMLApi);

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

        assertThat(json.get("error").asText()).contains("Course not found");
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

        assertThat(json.get("error").asText()).contains("Failed to get ML-based");
    }
}
