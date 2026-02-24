package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphNodeDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphNodeDTO.CompetencyNodeValueType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.MapCompetencyToCompetencyRequestDTO;

/**
 * Unit tests for Atlas Agent DTOs.
 * Tests JSON serialization/deserialization and record functionality.
 */
class AtlasAgentDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Nested
    class CompetencyGraphNodeDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyGraphNodeDTO dto = new CompetencyGraphNodeDTO("1", "OOP Basics", ZonedDateTime.now(), 85.5, CompetencyNodeValueType.MASTERY_PROGRESS);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"id\":\"1\"");
            assertThat(json).contains("\"label\":\"OOP Basics\"");
            assertThat(json).contains("\"value\":85.5");
            assertThat(json).contains("\"valueType\":\"MASTERY_PROGRESS\"");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"id\":\"2\",\"label\":\"Design Patterns\",\"value\":72.3,\"valueType\":\"AVERAGE_MASTERY_PROGRESS\"}";

            CompetencyGraphNodeDTO dto = objectMapper.readValue(json, CompetencyGraphNodeDTO.class);

            assertThat(dto.id()).isEqualTo("2");
            assertThat(dto.label()).isEqualTo("Design Patterns");
            assertThat(dto.value()).isEqualTo(72.3);
            assertThat(dto.valueType()).isEqualTo(CompetencyNodeValueType.AVERAGE_MASTERY_PROGRESS);
        }

        @Test
        void shouldCreateFromDomainObject() {
            Competency competency = new Competency();
            competency.setId(10L);
            competency.setTitle("Data Structures");
            competency.setSoftDueDate(ZonedDateTime.now());

            CompetencyGraphNodeDTO dto = CompetencyGraphNodeDTO.of(competency, 90.0, CompetencyNodeValueType.MASTERY_PROGRESS);

            assertThat(dto.id()).isEqualTo("10");
            assertThat(dto.label()).isEqualTo("Data Structures");
            assertThat(dto.value()).isEqualTo(90.0);
            assertThat(dto.valueType()).isEqualTo(CompetencyNodeValueType.MASTERY_PROGRESS);
        }

        @Test
        void shouldHandleNullValues() throws JsonProcessingException {
            CompetencyGraphNodeDTO dto = new CompetencyGraphNodeDTO("3", "Test", null, null, null);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"id\":\"3\"");
            assertThat(json).contains("\"label\":\"Test\"");
            assertThat(json).doesNotContain("\"softDueDate\"");
            assertThat(json).doesNotContain("\"value\"");
            assertThat(json).doesNotContain("\"valueType\"");
        }

        @Test
        void shouldHandleEnumValues() {
            assertThat(CompetencyNodeValueType.MASTERY_PROGRESS.name()).isEqualTo("MASTERY_PROGRESS");
            assertThat(CompetencyNodeValueType.AVERAGE_MASTERY_PROGRESS.name()).isEqualTo("AVERAGE_MASTERY_PROGRESS");
            assertThat(CompetencyNodeValueType.values()).hasSize(2);
        }
    }

    @Nested
    class CompetencyGraphEdgeDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyGraphEdgeDTO dto = new CompetencyGraphEdgeDTO("edge-1", "1", "2", RelationType.ASSUMES);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"id\":\"edge-1\"");
            assertThat(json).contains("\"source\":\"1\"");
            assertThat(json).contains("\"target\":\"2\"");
            assertThat(json).contains("\"relationType\":\"ASSUMES\"");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"id\":\"edge-2\",\"source\":\"3\",\"target\":\"4\",\"relationType\":\"EXTENDS\"}";

            CompetencyGraphEdgeDTO dto = objectMapper.readValue(json, CompetencyGraphEdgeDTO.class);

            assertThat(dto.id()).isEqualTo("edge-2");
            assertThat(dto.source()).isEqualTo("3");
            assertThat(dto.target()).isEqualTo("4");
            assertThat(dto.relationType()).isEqualTo(RelationType.EXTENDS);
        }

        @Test
        void shouldCreateFromDomainObject() {
            Competency head = new Competency();
            head.setId(10L);
            Competency tail = new Competency();
            tail.setId(20L);

            CompetencyRelation relation = new CompetencyRelation();
            relation.setId(100L);
            relation.setHeadCompetency(head);
            relation.setTailCompetency(tail);
            relation.setType(RelationType.MATCHES);

            CompetencyGraphEdgeDTO dto = CompetencyGraphEdgeDTO.of(relation);

            assertThat(dto.id()).isEqualTo("100");
            assertThat(dto.source()).isEqualTo("10");
            assertThat(dto.target()).isEqualTo("20");
            assertThat(dto.relationType()).isEqualTo(RelationType.MATCHES);
        }

        @Test
        void shouldHandleAllRelationTypes() throws JsonProcessingException {
            for (RelationType relationType : RelationType.values()) {
                CompetencyGraphEdgeDTO dto = new CompetencyGraphEdgeDTO("edge-test", "1", "2", relationType);
                String json = objectMapper.writeValueAsString(dto);
                CompetencyGraphEdgeDTO back = objectMapper.readValue(json, CompetencyGraphEdgeDTO.class);

                assertThat(back.relationType()).isEqualTo(relationType);
            }
        }
    }

    @Nested
    class CompetencyRelationPreviewDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyRelationPreviewDTO dto = new CompetencyRelationPreviewDTO(1L, 10L, "Head Title", 20L, "Tail Title", RelationType.ASSUMES, false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"relationId\":1");
            assertThat(json).contains("\"headCompetencyId\":10");
            assertThat(json).contains("\"headCompetencyTitle\":\"Head Title\"");
            assertThat(json).contains("\"tailCompetencyId\":20");
            assertThat(json).contains("\"tailCompetencyTitle\":\"Tail Title\"");
            assertThat(json).contains("\"relationType\":\"ASSUMES\"");
            assertThat(json).contains("\"viewOnly\":false");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"relationId\":2,\"headCompetencyId\":30,\"headCompetencyTitle\":\"OOP\",\"tailCompetencyId\":40,\"tailCompetencyTitle\":\"Patterns\",\"relationType\":\"EXTENDS\",\"viewOnly\":true}";

            CompetencyRelationPreviewDTO dto = objectMapper.readValue(json, CompetencyRelationPreviewDTO.class);

            assertThat(dto.relationId()).isEqualTo(2L);
            assertThat(dto.headCompetencyId()).isEqualTo(30L);
            assertThat(dto.headCompetencyTitle()).isEqualTo("OOP");
            assertThat(dto.tailCompetencyId()).isEqualTo(40L);
            assertThat(dto.tailCompetencyTitle()).isEqualTo("Patterns");
            assertThat(dto.relationType()).isEqualTo(RelationType.EXTENDS);
            assertThat(dto.viewOnly()).isTrue();
        }

        @Test
        void shouldHandleNullRelationId() throws JsonProcessingException {
            CompetencyRelationPreviewDTO dto = new CompetencyRelationPreviewDTO(null, 10L, "Head", 20L, "Tail", RelationType.MATCHES, null);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).doesNotContain("\"relationId\"");
            assertThat(json).doesNotContain("\"viewOnly\"");
        }
    }

    @Nested
    class SingleRelationPreviewResponseDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyRelationPreviewDTO relation = new CompetencyRelationPreviewDTO(1L, 10L, "Head", 20L, "Tail", RelationType.ASSUMES, false);
            SingleRelationPreviewResponseDTO dto = new SingleRelationPreviewResponseDTO(true, relation, false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"preview\":true");
            assertThat(json).contains("\"relation\":{");
            assertThat(json).contains("\"viewOnly\":false");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"preview\":true,\"relation\":{\"relationId\":1,\"headCompetencyId\":10,\"headCompetencyTitle\":\"OOP\",\"tailCompetencyId\":20,\"tailCompetencyTitle\":\"Patterns\",\"relationType\":\"EXTENDS\"},\"viewOnly\":true}";

            SingleRelationPreviewResponseDTO dto = objectMapper.readValue(json, SingleRelationPreviewResponseDTO.class);

            assertThat(dto.preview()).isTrue();
            assertThat(dto.relation()).isNotNull();
            assertThat(dto.relation().headCompetencyId()).isEqualTo(10L);
            assertThat(dto.viewOnly()).isTrue();
        }

        @Test
        void shouldHandlePreviewFalse() throws JsonProcessingException {
            SingleRelationPreviewResponseDTO dto = new SingleRelationPreviewResponseDTO(false, null, false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"preview\":false");
        }
    }

    @Nested
    class BatchRelationPreviewResponseDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyRelationPreviewDTO rel1 = new CompetencyRelationPreviewDTO(null, 1L, "A", 2L, "B", RelationType.ASSUMES, null);
            CompetencyRelationPreviewDTO rel2 = new CompetencyRelationPreviewDTO(null, 3L, "C", 4L, "D", RelationType.EXTENDS, null);
            BatchRelationPreviewResponseDTO dto = new BatchRelationPreviewResponseDTO(true, 2, List.of(rel1, rel2), false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"batchPreview\":true");
            assertThat(json).contains("\"count\":2");
            assertThat(json).contains("\"relations\":[");
            assertThat(json).contains("\"viewOnly\":false");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"batchPreview\":true,\"count\":2,\"relations\":[{\"headCompetencyId\":1,\"headCompetencyTitle\":\"A\",\"tailCompetencyId\":2,\"tailCompetencyTitle\":\"B\",\"relationType\":\"ASSUMES\"},{\"headCompetencyId\":3,\"headCompetencyTitle\":\"C\",\"tailCompetencyId\":4,\"tailCompetencyTitle\":\"D\",\"relationType\":\"EXTENDS\"}],\"viewOnly\":true}";

            BatchRelationPreviewResponseDTO dto = objectMapper.readValue(json, BatchRelationPreviewResponseDTO.class);

            assertThat(dto.batchPreview()).isTrue();
            assertThat(dto.count()).isEqualTo(2);
            assertThat(dto.relations()).hasSize(2);
            assertThat(dto.viewOnly()).isTrue();
        }

        @Test
        void shouldHandleEmptyRelationsList() throws JsonProcessingException {
            BatchRelationPreviewResponseDTO dto = new BatchRelationPreviewResponseDTO(false, 0, List.of(), false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"batchPreview\":false");
            assertThat(json).contains("\"count\":0");
            assertThat(json).doesNotContain("\"relations\"");
        }

        @Test
        void shouldHandleNullRelationsList() throws JsonProcessingException {
            BatchRelationPreviewResponseDTO dto = new BatchRelationPreviewResponseDTO(true, 0, null, false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).doesNotContain("\"relations\"");
        }
    }

    @Nested
    class RelationGraphPreviewDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyGraphNodeDTO node1 = new CompetencyGraphNodeDTO("1", "Node1", null, null, null);
            CompetencyGraphNodeDTO node2 = new CompetencyGraphNodeDTO("2", "Node2", null, null, null);
            CompetencyGraphEdgeDTO edge = new CompetencyGraphEdgeDTO("edge-1", "1", "2", RelationType.ASSUMES);

            RelationGraphPreviewDTO dto = new RelationGraphPreviewDTO(List.of(node1, node2), List.of(edge), false);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"nodes\":[");
            assertThat(json).contains("\"edges\":[");
            assertThat(json).contains("\"viewOnly\":false");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"nodes\":[{\"id\":\"1\",\"label\":\"A\"},{\"id\":\"2\",\"label\":\"B\"}],\"edges\":[{\"id\":\"edge-1\",\"source\":\"1\",\"target\":\"2\",\"relationType\":\"EXTENDS\"}],\"viewOnly\":true}";

            RelationGraphPreviewDTO dto = objectMapper.readValue(json, RelationGraphPreviewDTO.class);

            assertThat(dto.nodes()).hasSize(2);
            assertThat(dto.edges()).hasSize(1);
            assertThat(dto.viewOnly()).isTrue();
        }

        @Test
        void shouldHandleEmptyNodesAndEdges() throws JsonProcessingException {
            RelationGraphPreviewDTO dto = new RelationGraphPreviewDTO(List.of(), List.of(), null);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).doesNotContain("\"nodes\"");
            assertThat(json).doesNotContain("\"edges\"");
        }

        @Test
        void shouldHandleNullViewOnly() throws JsonProcessingException {
            CompetencyGraphNodeDTO node = new CompetencyGraphNodeDTO("1", "Node1", null, null, null);
            CompetencyGraphEdgeDTO edge = new CompetencyGraphEdgeDTO("edge-1", "1", "1", RelationType.MATCHES);

            RelationGraphPreviewDTO dto = new RelationGraphPreviewDTO(List.of(node), List.of(edge), null);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).doesNotContain("\"viewOnly\"");
        }
    }

    @Nested
    class MapCompetencyToCompetencyRequestDTOTests {

        @Test
        void shouldSerializeToJsonWithCorrectPropertyNames() throws JsonProcessingException {
            MapCompetencyToCompetencyRequestDTO dto = new MapCompetencyToCompetencyRequestDTO(1L, 2L);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"source_competency_id\":1");
            assertThat(json).contains("\"target_competency_id\":2");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"source_competency_id\":10,\"target_competency_id\":20}";

            MapCompetencyToCompetencyRequestDTO dto = objectMapper.readValue(json, MapCompetencyToCompetencyRequestDTO.class);

            assertThat(dto.sourceCompetencyId()).isEqualTo(10L);
            assertThat(dto.targetCompetencyId()).isEqualTo(20L);
        }

        @Test
        void shouldRoundTripSuccessfully() throws JsonProcessingException {
            MapCompetencyToCompetencyRequestDTO original = new MapCompetencyToCompetencyRequestDTO(100L, 200L);

            String json = objectMapper.writeValueAsString(original);
            MapCompetencyToCompetencyRequestDTO back = objectMapper.readValue(json, MapCompetencyToCompetencyRequestDTO.class);

            assertThat(back.sourceCompetencyId()).isEqualTo(original.sourceCompetencyId());
            assertThat(back.targetCompetencyId()).isEqualTo(original.targetCompetencyId());
        }
    }

    @Nested
    class CompetencyRelationDTOTests {

        @Test
        void shouldSerializeToJson() throws JsonProcessingException {
            CompetencyRelationDTO dto = new CompetencyRelationDTO(1L, 10L, 20L, RelationType.ASSUMES);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).contains("\"id\":1");
            assertThat(json).contains("\"headCompetencyId\":20");
            assertThat(json).contains("\"tailCompetencyId\":10");
            assertThat(json).contains("\"relationType\":\"ASSUMES\"");
        }

        @Test
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = "{\"id\":2,\"headCompetencyId\":30,\"tailCompetencyId\":40,\"relationType\":\"EXTENDS\"}";

            CompetencyRelationDTO dto = objectMapper.readValue(json, CompetencyRelationDTO.class);

            assertThat(dto.id()).isEqualTo(2L);
            assertThat(dto.headCompetencyId()).isEqualTo(30L);
            assertThat(dto.tailCompetencyId()).isEqualTo(40L);
            assertThat(dto.relationType()).isEqualTo(RelationType.EXTENDS);
        }

        @Test
        void shouldCreateFromDomainObject() {
            Competency head = new Competency();
            head.setId(10L);
            Competency tail = new Competency();
            tail.setId(20L);

            CompetencyRelation relation = new CompetencyRelation();
            relation.setId(5L);
            relation.setHeadCompetency(head);
            relation.setTailCompetency(tail);
            relation.setType(RelationType.MATCHES);

            CompetencyRelationDTO dto = CompetencyRelationDTO.of(relation);

            assertThat(dto.id()).isEqualTo(5L);
            assertThat(dto.headCompetencyId()).isEqualTo(10L);
            assertThat(dto.tailCompetencyId()).isEqualTo(20L);
            assertThat(dto.relationType()).isEqualTo(RelationType.MATCHES);
        }

        @Test
        void shouldHandleNullId() throws JsonProcessingException {
            CompetencyRelationDTO dto = new CompetencyRelationDTO(null, 10L, 20L, RelationType.ASSUMES);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).doesNotContain("\"id\":");
        }
    }
}
