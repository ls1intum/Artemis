package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class GradingCriteriaMapperTest {

    @Test
    void createsCriteriaAndInstructionsInRequestedNonAlphabeticalOrder() {
        var exercise = new TextExercise();
        var zulu = new GradingInstructionDTO(13L, 1, "Good", "Zulu instruction", "Zulu feedback", 0);
        var alpha = new GradingInstructionDTO(11L, 2, "Good", "Alpha instruction", "Alpha feedback", 0);
        var middle = new GradingInstructionDTO(12L, 3, "Good", "Middle instruction", "Middle feedback", 0);
        var criteria = GradingCriteriaMapper.create(List.of(new GradingCriterionDTO(3L, "Zulu category", List.of(zulu, alpha, middle)),
                new GradingCriterionDTO(1L, "Alpha category", List.of()), new GradingCriterionDTO(2L, "Middle category", List.of())), exercise);

        assertThat(criteria).extracting(GradingCriterion::getTitle).containsExactly("Zulu category", "Alpha category", "Middle category");
        assertThat(criteria.iterator().next().getStructuredGradingInstructions()).extracting(GradingInstruction::getInstructionDescription).containsExactly("Zulu instruction",
                "Alpha instruction", "Middle instruction");
        assertThat(GradingCriterionDTO.ofAll(new HashSet<>(criteria))).extracting(GradingCriterionDTO::title).containsExactly("Zulu category", "Alpha category", "Middle category");
        var firstCriterion = criteria.iterator().next();
        firstCriterion.setStructuredGradingInstructions(new HashSet<>(firstCriterion.getStructuredGradingInstructions()));
        assertThat(GradingCriterionDTO.of(firstCriterion).structuredGradingInstructions()).extracting(GradingInstructionDTO::instructionDescription)
                .containsExactly("Zulu instruction", "Alpha instruction", "Middle instruction");
    }

    @Test
    void reordersExistingCriteriaAndInstructionsWithoutReplacingManagedCollectionsOrRows() {
        var exercise = new TextExercise();
        var criterion = new GradingCriterion();
        criterion.setId(1L);
        var firstInstruction = new GradingInstruction();
        firstInstruction.setId(11L);
        var secondInstruction = new GradingInstruction();
        secondInstruction.setId(12L);
        criterion.setStructuredGradingInstructions(new LinkedHashSet<>(List.of(firstInstruction, secondInstruction)));
        var secondCriterion = new GradingCriterion();
        secondCriterion.setId(2L);
        exercise.setGradingCriteria(new LinkedHashSet<>(List.of(criterion, secondCriterion)));
        var managedCriteria = exercise.getGradingCriteria();
        var managedInstructions = criterion.getStructuredGradingInstructions();

        GradingCriteriaMapper.replace(List.of(new GradingCriterionDTO(2L, "Zulu category", List.of()),
                new GradingCriterionDTO(1L, "Alpha category", List.of(GradingInstructionDTO.of(secondInstruction), GradingInstructionDTO.of(firstInstruction)))), exercise);

        assertThat(exercise.getGradingCriteria()).isSameAs(managedCriteria).containsExactly(secondCriterion, criterion);
        assertThat(criterion.getStructuredGradingInstructions()).isSameAs(managedInstructions).containsExactly(secondInstruction, firstInstruction);
    }

    @Test
    void newRubricsKeepAllUnpersistedCriteriaAndTheirOwner() {
        var exercise = new TextExercise();
        var first = new GradingCriterionDTO(null, "Same title", List.of());
        var second = new GradingCriterionDTO(null, "Same title", List.of());
        var criteria = GradingCriteriaMapper.create(List.of(first, second), exercise);
        assertThat(criteria).hasSize(2).allSatisfy(criterion -> assertThat(criterion.getExercise()).isSameAs(exercise));
        assertThat(GradingCriteriaMapper.create(null, exercise)).isEmpty();
    }

    @Test
    void reconcilesManagedCriteriaWithoutReplacingReferencedInstructions() {
        var exercise = new TextExercise();
        var retained = new GradingCriterion();
        retained.setId(1L);
        retained.setExercise(exercise);
        var instruction = new GradingInstruction();
        instruction.setId(11L);
        instruction.setCredits(1.0);
        instruction.setGradingCriterion(retained);
        retained.setStructuredGradingInstructions(new HashSet<>(List.of(instruction)));
        var removed = new GradingCriterion();
        removed.setId(2L);
        exercise.setGradingCriteria(new HashSet<>(List.of(retained, removed)));
        var managedCollection = exercise.getGradingCriteria();
        var instructionDto = GradingInstructionDTO.of(instruction);
        var update = new GradingCriterionDTO(1L, "Updated rubric", List.of(instructionDto));
        var created = new GradingCriterionDTO(null, "New rubric", List.of());
        GradingCriteriaMapper.replace(List.of(update, created), exercise);
        assertThat(exercise.getGradingCriteria()).isSameAs(managedCollection).hasSize(2).contains(retained).doesNotContain(removed);
        assertThat(retained.getTitle()).isEqualTo("Updated rubric");
        assertThat(retained.getStructuredGradingInstructions()).containsExactly(instruction);
        assertThat(retained.getStructuredGradingInstructions().iterator().next()).isSameAs(instruction);
        assertThat(exercise.getGradingCriteria()).filteredOn(criterion -> criterion.getId() == null).singleElement()
                .satisfies(criterion -> assertThat(criterion.getExercise()).isSameAs(exercise));
    }

    @Test
    void emptyRequestClearsExistingCriteriaInPlace() {
        var exercise = new TextExercise();
        var criterion = new GradingCriterion();
        exercise.setGradingCriteria(new HashSet<>(List.of(criterion)));
        var managedCollection = exercise.getGradingCriteria();
        GradingCriteriaMapper.replace(List.of(), exercise);
        assertThat(exercise.getGradingCriteria()).isSameAs(managedCollection).isEmpty();
    }

    @Test
    void copiedRubricsKeepPersistedPositionsAndTrackNewInstructionInstances() {
        var exercise = new TextExercise();
        var criterion = new GradingCriterion();
        criterion.setId(5L);
        criterion.setTitle("Rubric to copy");
        criterion.setExercise(exercise);
        criterion.setSortOrder(4_000_000_001L);
        var instruction = new GradingInstruction();
        instruction.setId(6L);
        instruction.setCredits(2.5);
        instruction.setFeedback("Clear explanation");
        instruction.setGradingScale("Good");
        instruction.setInstructionDescription("Explain the result");
        instruction.setUsageCount(3);
        instruction.setSortOrder(4_000_000_002L);
        criterion.addStructuredGradingInstruction(instruction);
        exercise.setGradingCriteria(new LinkedHashSet<>(List.of(criterion)));
        var tracker = new HashMap<Long, GradingInstruction>();

        var copied = exercise.copyGradingCriteria(tracker);

        assertThat(copied).singleElement().satisfies(copy -> {
            assertThat(copy).isNotSameAs(criterion);
            assertThat(copy.getId()).isNull();
            assertThat(copy.getTitle()).isEqualTo("Rubric to copy");
            assertThat(copy.getExercise()).isSameAs(exercise);
            assertThat(copy.getSortOrder()).isEqualTo(4_000_000_001L);
            assertThat(copy.getStructuredGradingInstructions()).singleElement().satisfies(copiedInstruction -> {
                assertThat(copiedInstruction.getId()).isNull();
                assertThat(copiedInstruction).isNotSameAs(instruction);
                assertThat(copiedInstruction.getCredits()).isEqualTo(2.5);
                assertThat(copiedInstruction.getFeedback()).isEqualTo("Clear explanation");
                assertThat(copiedInstruction.getGradingScale()).isEqualTo("Good");
                assertThat(copiedInstruction.getInstructionDescription()).isEqualTo("Explain the result");
                assertThat(copiedInstruction.getUsageCount()).isEqualTo(3);
                assertThat(copiedInstruction.getFeedbacks()).isEmpty();
                assertThat(copiedInstruction.getSortOrder()).isEqualTo(4_000_000_002L);
                assertThat(copiedInstruction.getGradingCriterion()).isSameAs(copy);
                assertThat(tracker.get(6L)).isSameAs(copiedInstruction);
            });
        });
        assertThat(criterion.getStructuredGradingInstructions()).containsExactly(instruction);
        assertThat(instruction.getGradingCriterion()).isSameAs(criterion);
    }
}
