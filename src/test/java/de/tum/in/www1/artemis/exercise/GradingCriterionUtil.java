package de.tum.in.www1.artemis.exercise;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import de.tum.in.www1.artemis.domain.*;

public final class GradingCriterionUtil {

    private GradingCriterionUtil() {
        throw new IllegalCallerException("utility class");
    }

    /**
     * Finds a grading criterion that matches the given requirement.
     *
     * @param gradingCriteria Some grading criteria.
     * @param check           A requirement that the returned grading criterion should fulfill.
     * @return A grading criterion, if it could be found.
     */
    public static Optional<GradingCriterion> findAnyWhere(final Set<GradingCriterion> gradingCriteria, final Predicate<GradingCriterion> check) {
        return gradingCriteria.stream().filter(check).findAny();
    }

    /**
     * Finds a grading criterion with the given title.
     *
     * @param exercise An exercise that has grading criteria.
     * @param title    The title of the grading criterion.
     * @return A grading criterion that has the required title.
     */
    public static GradingCriterion findGradingCriterionByTitle(Exercise exercise, String title) {
        return findAnyWhere(exercise.getGradingCriteria(), criterion -> Objects.equals(title, criterion.getTitle())).orElseThrow();
    }

    /**
     * Finds a grading instruction in any of the criteria that matches the given requirement.
     *
     * @param gradingCriteria Some grading criteria.
     * @param check           A requirement that the returned grading instruction should fulfill.
     * @return A grading instruction, if it could be found.
     */
    public static Optional<GradingInstruction> findAnyInstructionWhere(final Set<GradingCriterion> gradingCriteria, final Predicate<GradingInstruction> check) {
        return gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream()).filter(check).findAny();
    }

    /**
     * Finds a grading instruction that has the required maximum usage count.
     *
     * @param gradingCriteria Some grading criteria.
     * @param count           The required {@link GradingInstruction#getUsageCount()}.
     * @return A grading instruction, if it could be found.
     */
    public static GradingInstruction findInstructionByMaxUsageCount(final Set<GradingCriterion> gradingCriteria, final int count) {
        return findAnyInstructionWhere(gradingCriteria, instruction -> instruction.getUsageCount() == count).orElseThrow();
    }
}
