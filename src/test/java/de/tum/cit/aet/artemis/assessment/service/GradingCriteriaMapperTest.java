package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class GradingCriteriaMapperTest {

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
}
