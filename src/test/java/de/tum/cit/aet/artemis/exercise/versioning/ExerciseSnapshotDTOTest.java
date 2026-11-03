package de.tum.cit.aet.artemis.exercise.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseSnapshotDTOTest {

    @Test
    void keepsAnEmptyRubricNullAndOmitsItFromSnapshotJson() {
        var exercise = new TextExercise();
        exercise.setId(42L);

        var snapshot = ExerciseSnapshotDTO.of(exercise, null, null);

        assertThat(snapshot.gradingCriteria()).isNull();
        assertThat(JsonObjectMapper.get().valueToTree(snapshot).has("gradingCriteria")).isFalse();
    }

    @Test
    void snapshotsCriteriaAndInstructionsInPersistedOrder() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        var zulu = criterion(30L, "Zulu category", 0);
        var alpha = criterion(10L, "Alpha category", 1);
        var middle = criterion(20L, "Middle category", 2);
        var zuluInstruction = instruction(90L, "Zulu instruction", 0);
        var alphaInstruction = instruction(80L, "Alpha instruction", 1);
        zulu.setStructuredGradingInstructions(new HashSet<>(List.of(alphaInstruction, zuluInstruction)));
        exercise.setGradingCriteria(new HashSet<>(List.of(alpha, middle, zulu)));

        var snapshot = ExerciseSnapshotDTO.of(exercise, null, null);

        assertThat(snapshot.gradingCriteria()).extracting(GradingCriterionDTO::title).containsExactly("Zulu category", "Alpha category", "Middle category");
        assertThat(snapshot.gradingCriteria().getFirst().structuredGradingInstructions()).extracting(GradingInstructionDTO::instructionDescription)
                .containsExactly("Zulu instruction", "Alpha instruction");
        var serialized = JsonObjectMapper.get().valueToTree(snapshot);
        assertThat(serialized.get("gradingCriteria").get(0).get("title").asString()).isEqualTo("Zulu category");
        assertThat(serialized.get("gradingCriteria").get(0).get("structuredGradingInstructions").get(0).get("instructionDescription").asString()).isEqualTo("Zulu instruction");
    }

    private static GradingCriterion criterion(long id, String title, long sortOrder) {
        var criterion = new GradingCriterion();
        criterion.setId(id);
        criterion.setTitle(title);
        criterion.setSortOrder(sortOrder);
        return criterion;
    }

    private static GradingInstruction instruction(long id, String description, long sortOrder) {
        var instruction = new GradingInstruction();
        instruction.setId(id);
        instruction.setInstructionDescription(description);
        instruction.setSortOrder(sortOrder);
        return instruction;
    }
}
