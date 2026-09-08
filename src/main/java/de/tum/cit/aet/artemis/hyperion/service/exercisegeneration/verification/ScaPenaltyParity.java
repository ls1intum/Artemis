package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.core.config.StaticCodeAnalysisConfigurer;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisDefaultCategory;

/** Determines which parsed static-code-analysis findings would reduce the exercise's production score. */
final class ScaPenaltyParity {

    /** A finding's producing tool and parsed category. */
    record ScaFinding(String tool, String category) {
    }

    private ScaPenaltyParity() {
    }

    /**
     * @param exercise            the exercise whose SCA configuration governs grading (must be the persisted exercise, so its language and id resolve the default mappings)
     * @param persistedCategories the exercise's persisted SCA categories (read the same way production does, {@code findByExerciseId}); their state/penalty decide grading
     * @param solutionFindings    the SCA findings the verifier extracted from the solution build's collected reports (tool + derived category)
     * @return the distinct findings that production would penalise (a graded, positively-penalised category); empty when production would deduct nothing
     */
    static List<ScaFinding> penalisingFindings(ProgrammingExercise exercise, Set<StaticCodeAnalysisCategory> persistedCategories, List<ScaFinding> solutionFindings) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
            return List.of();
        }
        // Mirrors calculateTotalPenalty: null means 100, and 0 disables the SCA penalty entirely.
        int maxPenalty = exercise.getMaxStaticCodeAnalysisPenalty() != null ? exercise.getMaxStaticCodeAnalysisPenalty() : 100;
        if (maxPenalty <= 0 || persistedCategories == null || persistedCategories.isEmpty() || solutionFindings == null || solutionFindings.isEmpty()) {
            return List.of();
        }
        List<StaticCodeAnalysisDefaultCategory> defaults = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(exercise.getProgrammingLanguage());
        if (defaults == null) {
            return List.of();
        }

        List<ScaFinding> penalising = new ArrayList<>();
        for (ScaFinding finding : solutionFindings) {
            if (finding == null || finding.tool() == null || finding.tool().isEmpty()) {
                continue;
            }
            String category = finding.category() == null ? "" : finding.category().trim();
            if (isPenalising(finding.tool().trim(), category, persistedCategories, defaults) && !penalising.contains(finding)) {
                penalising.add(finding);
            }
        }
        return penalising;
    }

    private static boolean isPenalising(String tool, String category, Set<StaticCodeAnalysisCategory> persistedCategories, List<StaticCodeAnalysisDefaultCategory> defaults) {
        for (StaticCodeAnalysisCategory persisted : persistedCategories) {
            if (persisted.getState() != CategoryState.GRADED) {
                continue;
            }
            if (persisted.getPenalty() == null || persisted.getPenalty() <= 0) {
                continue;
            }
            StaticCodeAnalysisDefaultCategory defaultMatch = defaults.stream().filter(d -> d.name().equals(persisted.getName())).findFirst().orElse(null);
            if (defaultMatch == null) {
                continue;
            }
            for (StaticCodeAnalysisDefaultCategory.CategoryMapping mapping : defaultMatch.categoryMappings()) {
                if (mapping.tool().name().equalsIgnoreCase(tool) && mapping.category().equalsIgnoreCase(category)) {
                    return true;
                }
            }
        }
        return false;
    }
}
