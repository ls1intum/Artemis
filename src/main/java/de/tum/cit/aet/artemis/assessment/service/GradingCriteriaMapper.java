package de.tum.cit.aet.artemis.assessment.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/** Reconciles rubric DTOs while retaining managed criteria and their feedback references. */
public final class GradingCriteriaMapper {

    private GradingCriteriaMapper() {
    }

    /**
     * Builds a new rubric with owner references for creation/import, without touching managed state.
     *
     * @param requested requested criteria, absent when no rubric was submitted
     * @param exercise  owner of the new criteria
     * @return new criteria retaining every unpersisted row
     */
    public static Set<GradingCriterion> create(Collection<GradingCriterionDTO> requested, Exercise exercise) {
        Set<GradingCriterion> criteria = GradingCriterionDTO.toEntities(requested);
        criteria.forEach(criterion -> criterion.setExercise(exercise));
        return criteria;
    }

    /**
     * Replaces the contents of the managed collection. Callers decide whether an absent or empty request clears it.
     *
     * @param requested criteria to retain, update or create
     * @param exercise  owner of the criteria
     */
    public static void replace(Collection<GradingCriterionDTO> requested, Exercise exercise) {
        Set<GradingCriterion> managedCriteria = exercise.ensureGradingCriteriaSet();
        Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(criterion -> criterion.getId() != null)
                .collect(Collectors.toMap(GradingCriterion::getId, criterion -> criterion, (first, second) -> first));
        Set<GradingCriterion> updated = new LinkedHashSet<>();
        long position = 0;
        for (GradingCriterionDTO dto : requested) {
            GradingCriterion criterion = dto.id() != null ? existingById.get(dto.id()) : null;
            if (criterion == null) {
                criterion = dto.toEntity();
                criterion.setExercise(exercise);
            }
            else {
                dto.applyTo(criterion);
            }
            criterion.setSortOrder(position++);
            updated.add(criterion);
        }
        managedCriteria.clear();
        managedCriteria.addAll(updated);
    }
}
