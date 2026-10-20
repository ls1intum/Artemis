package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO.ExerciseLinkRefDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO.LectureUnitRefDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO.RelationRefDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO.UnassignedExerciseRefDTO;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorPromptRenderer.ExerciseChange;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorPromptRenderer.LectureUnitChange;

class OrchestratorPromptRendererTest {

    @Test
    void emptyIndexExplainsBothCompetencyAndExerciseState() {
        assertThat(OrchestratorPromptRenderer.renderCompetencyIndex(new CompetencyIndexResponseDTO(List.of(), List.of()))).isEqualTo("""
                (no competencies defined in this course yet)

                UNASSIGNED EXERCISES (currently linked to no competency):
                (all course exercises are linked to at least one competency)""");
    }

    @Test
    void indexRendersAllChildGroupCombinationsAndNullMetadataFallbacks() {
        var bare = new CompetencyIndexDTO(1L, "Bare", null, "competency", List.of(), List.of(), List.of());
        var exercises = List.of(new ExerciseLinkRefDTO("A", null, null), new ExerciseLinkRefDTO("B", "text", 0.5));
        var units = List.of(new LectureUnitRefDTO("Notes", null), new LectureUnitRefDTO("Slides", "attachment"));
        var relation = List.of(new RelationRefDTO(1L, 2L, RelationType.ASSUMES));
        var withUnits = new CompetencyIndexDTO(2L, "Units", CompetencyTaxonomy.APPLY, "competency", exercises, units, List.of());
        var withRelations = new CompetencyIndexDTO(3L, "Relations", CompetencyTaxonomy.ANALYZE, "competency", exercises, List.of(), relation);
        var withBoth = new CompetencyIndexDTO(4L, "Both", CompetencyTaxonomy.CREATE, "competency", exercises, units, relation);
        var index = new CompetencyIndexResponseDTO(List.of(bare, withUnits, withRelations, withBoth),
                List.of(new UnassignedExerciseRefDTO(9L, null, null), new UnassignedExerciseRefDTO(10L, "Quiz", "quiz")));
        String rendered = OrchestratorPromptRenderer.renderCompetencyIndex(index);
        assertThat(rendered).contains("├── [1] Bare (competency, UNSPECIFIED)\n│   └── exercises: —", "A (unknown)", "B (text, w=0.5)", "Notes (unknown)", "Slides (attachment)",
                "1 --ASSUMES--> 2", "└── [4] Both (competency, CREATE)", "├── [9] (untitled) (unknown)", "└── [10] Quiz (quiz)");
        assertThat(rendered).doesNotContain("null");
    }

    @Test
    void changeBatchNumbersAcrossTypesAndFiltersEmptySourceMetadata() {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("lectureUnitType", "text");
        metadata.put("null", null);
        metadata.put("blank", " ");
        metadata.put("source", "https://example.org");
        metadata.put("attachment", "slides.pdf");
        var rendered = OrchestratorPromptRenderer.renderChangeBatch(List.of(new ExerciseChange(1L, "Exercise", null), new ExerciseChange(2L, "Second", " ")), List
                .of(new LectureUnitChange(3L, "Notes", null, metadata), new LectureUnitChange(4L, "Empty", " ", Map.of()), new LectureUnitChange(5L, "Text", "Learning", null)));
        assertThat(rendered).isEqualTo("""
                1. [UPDATE exercise id=1] Exercise
                (no problem statement available)

                2. [UPDATE exercise id=2] Second
                (no problem statement available)

                3. [UPDATE lecture-unit id=3] Notes
                (no learning text available)
                Source metadata: source=https://example.org, attachment=slides.pdf

                4. [UPDATE lecture-unit id=4] Empty
                (no learning text available)

                5. [UPDATE lecture-unit id=5] Text
                Learning""");
    }

    @Test
    void lectureOnlyBatchStartsAtOneAndTypeOnlyMetadataDoesNotLeak() {
        assertThat(OrchestratorPromptRenderer.renderChangeBatch(List.of(), List.of(new LectureUnitChange(3L, "Notes", "Learning", Map.of("lectureUnitType", "text")))))
                .isEqualTo("1. [UPDATE lecture-unit id=3] Notes\nLearning");
    }
}
