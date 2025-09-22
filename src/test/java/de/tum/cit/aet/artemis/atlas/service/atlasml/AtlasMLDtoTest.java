package de.tum.cit.aet.artemis.atlas.service.atlasml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLExerciseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.core.domain.Course;

class AtlasMLDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void testSuggestCompetencyRequestDTO_jsonRoundTrip() throws JsonProcessingException {
        SuggestCompetencyRequestDTO dto = new SuggestCompetencyRequestDTO("desc", 42L);
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("course_id");
        SuggestCompetencyRequestDTO back = objectMapper.readValue(json, SuggestCompetencyRequestDTO.class);

        assertThat(back.description()).isEqualTo("desc");
        assertThat(back.courseId()).isEqualTo(42L);
    }

    @Test
    void testAtlasMLCompetencyDTO_mapping() {
        Course course = new Course();
        course.setId(5L);

        Competency competency = new Competency("t", "d", null, 100, CompetencyTaxonomy.APPLY, false);
        competency.setId(3L);
        competency.setCourse(course);

        AtlasMLCompetencyDTO dto = AtlasMLCompetencyDTO.fromDomain(competency);
        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.title()).isEqualTo("t");
        assertThat(dto.description()).isEqualTo("d");
        assertThat(dto.courseId()).isEqualTo(5L);

        Competency back = dto.toDomain();
        assertThat(back.getTitle()).isEqualTo("t");
        assertThat(back.getDescription()).isEqualTo("d");
    }

    @Test
    void testAtlasMLCompetencyDTO_nullMapping() throws JsonProcessingException {
        AtlasMLCompetencyDTO fromNull = AtlasMLCompetencyDTO.fromDomain(null);
        assertThat(fromNull).isNull();

        SuggestCompetencyResponseDTO respNull = new SuggestCompetencyResponseDTO(null);
        assertThat(respNull.toDomainCompetencies()).isEmpty();
    }

    @Test
    void testAtlasMLExerciseDTO_json() throws JsonProcessingException {
        AtlasMLExerciseDTO dto = new AtlasMLExerciseDTO(7L, "ex", "desc", List.of(1L, 2L), 9L);
        String json = objectMapper.writeValueAsString(dto);
        AtlasMLExerciseDTO back = objectMapper.readValue(json, AtlasMLExerciseDTO.class);

        assertThat(back.id()).isEqualTo(7L);
        assertThat(back.title()).isEqualTo("ex");
        assertThat(back.description()).isEqualTo("desc");
        assertThat(back.competencies()).containsExactly(1L, 2L);
        assertThat(back.courseId()).isEqualTo(9L);
    }

    @Test
    void testAtlasMLCompetencyRelationDTO_mappingAndJson() throws JsonProcessingException {
        CompetencyRelation relation = new CompetencyRelation();
        relation.setType(RelationType.EXTENDS);
        // Set tail and head competencies to avoid null pointer when mapping ids
        Competency tail = new Competency();
        tail.setId(1L);
        Competency head = new Competency();
        head.setId(2L);
        relation.setTailCompetency(tail);
        relation.setHeadCompetency(head);

        AtlasMLCompetencyRelationDTO dto = AtlasMLCompetencyRelationDTO.fromDomain(relation);
        assertThat(dto.relationType()).isEqualTo("EXTENDS");

        String json = objectMapper.writeValueAsString(dto);
        AtlasMLCompetencyRelationDTO back = objectMapper.readValue(json, AtlasMLCompetencyRelationDTO.class);
        assertThat(back.relationType()).isEqualTo("EXTENDS");

        CompetencyRelation mapped = back.toDomain();
        assertThat(mapped.getType()).isEqualTo(RelationType.EXTENDS);
    }

    @Test
    void testAtlasMLCompetencyRelationDTO_defaultsAndInvalids() {
        // fromDomain null
        AtlasMLCompetencyRelationDTO fromNull = AtlasMLCompetencyRelationDTO.fromDomain(null);
        assertThat(fromNull).isNull();

        // invalid relation type should default
        AtlasMLCompetencyRelationDTO invalid = new AtlasMLCompetencyRelationDTO(1L, 2L, "INVALID");
        CompetencyRelation mapped = invalid.toDomain();
        assertThat(mapped.getType()).isEqualTo(RelationType.ASSUMES);

        // null relation type should default
        AtlasMLCompetencyRelationDTO withNull = new AtlasMLCompetencyRelationDTO(1L, 2L, null);
        CompetencyRelation mappedNull = withNull.toDomain();
        assertThat(mappedNull.getType()).isEqualTo(RelationType.ASSUMES);
    }

    @Test
    void testSaveCompetencyRequestDTO_factories() {
        // fromCompetency
        Competency comp = new Competency("t", "d", null, 100, CompetencyTaxonomy.APPLY, false);
        comp.setId(1L);
        Course course = new Course();
        course.setId(7L);
        comp.setCourse(course);
        SaveCompetencyRequestDTO r1 = SaveCompetencyRequestDTO.fromCompetency(comp, SaveCompetencyRequestDTO.OperationTypeDTO.UPDATE);
        assertThat(r1.competencies()).hasSize(1);
        assertThat(r1.operationType()).isEqualTo("UPDATE");

        // fromCompetencies
        SaveCompetencyRequestDTO r2 = SaveCompetencyRequestDTO.fromCompetencies(List.of(comp), SaveCompetencyRequestDTO.OperationTypeDTO.DELETE);
        assertThat(r2.competencies()).hasSize(1);
        assertThat(r2.operationType()).isEqualTo("DELETE");

        // fromExercise
        SaveCompetencyRequestDTO r3 = SaveCompetencyRequestDTO.fromExercise(10L, "ex", "desc", List.of(1L, 2L), 9L, SaveCompetencyRequestDTO.OperationTypeDTO.UPDATE);
        assertThat(r3.exercise()).isNotNull();
        assertThat(r3.exercise().id()).isEqualTo(10L);
        assertThat(r3.exercise().competencies()).containsExactly(1L, 2L);
    }

    @Test
    void testSaveCompetencyRequestDTO_nullBranches() throws JsonProcessingException {
        SaveCompetencyRequestDTO r1 = SaveCompetencyRequestDTO.fromCompetencies(List.of(), SaveCompetencyRequestDTO.OperationTypeDTO.UPDATE);
        assertThat(r1.competencies()).isNull();

        SaveCompetencyRequestDTO r2 = new SaveCompetencyRequestDTO(null, null, SaveCompetencyRequestDTO.OperationTypeDTO.DELETE.value());
        String json = objectMapper.writeValueAsString(r2);
        assertThat(json).contains("operation_type");
    }

    @Test
    void testSuggestCompetencyRelationsResponseDTO_nullBranches() {
        SuggestCompetencyRelationsResponseDTO respNull = new SuggestCompetencyRelationsResponseDTO(null);
        assertThat(respNull.toDomainRelations()).isEmpty();
    }

    @Test
    void testSuggestResponses_toDomain() {
        SuggestCompetencyResponseDTO resp = new SuggestCompetencyResponseDTO(List.of(new AtlasMLCompetencyDTO(1L, "t", "d", 2L)));
        List<Competency> comps = resp.toDomainCompetencies();
        assertThat(comps).hasSize(1);
        assertThat(comps.get(0).getTitle()).isEqualTo("t");

        SuggestCompetencyRelationsResponseDTO rels = new SuggestCompetencyRelationsResponseDTO(List.of(new AtlasMLCompetencyRelationDTO(1L, 2L, "ASSUMES")));
        List<CompetencyRelation> mapped = rels.toDomainRelations();
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).getType()).isEqualTo(RelationType.ASSUMES);
    }
}
