package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.core.config.StaticCodeAnalysisConfigurer;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisDefaultCategory;

/**
 * Pure (no Spring, no IO) replica of the SLICE of production's static-code-analysis grading that decides whether a finding would dock the score, so the Hyperion oracle can REJECT
 * a
 * reference solution that production would grade below 100% because of an SCA penalty.
 * <p>
 * <strong>Why this exists.</strong> When an exercise has static code analysis enabled, both the sandbox {@code verify.sh} and production run the same {@code *_static.yaml} phases,
 * so the SCA tools (SpotBugs/Checkstyle/PMD, ruff/clippy/eslint/…) execute in BOTH. But their reports carry no JUnit {@code <testcase>}, so the sandbox's JUnit-XML aggregation
 * ignores them and the differential oracle saw the solution as a 100% pass — while production folds an SCA penalty into the score
 * ({@code ProgrammingExerciseGradingService.calculateTotalPenalty}). A reference solution with graded SCA violations was therefore ACCEPTED by the oracle yet grades below 100% for
 * a
 * student. {@code verify.sh} now emits a {@code HYPERION_SCA <TOOL>|<rawCategory>} line per solution-build SCA finding; this class decides which of those production would actually
 * penalise.
 * <p>
 * <strong>Faithful to {@code calculateTotalPenalty}.</strong> Production deducts an SCA penalty iff ALL hold:
 * <ol>
 * <li>{@code exercise.isStaticCodeAnalysisEnabled()} is {@code TRUE},</li>
 * <li>{@code maxStaticCodeAnalysisPenalty} (default {@code 100} when {@code null}) {@code > 0}, and</li>
 * <li>a finding maps — via the {@code StaticCodeAnalysisConfigurer} default {@code (tool, category)} mappings, matched to the exercise's PERSISTED category by name, exactly as
 * {@code ProgrammingExerciseFeedbackCreationService.findCategoryForIssue}/{@code getCategoriesWithMappingForExercise} — to a category whose state is {@code GRADED} and whose
 * {@code penalty > 0} (a {@code GRADED} category with penalty {@code 0} contributes {@code 0} points: {@code categoryFeedback.size() * category.getPenalty()}).</li>
 * </ol>
 * When any of these is false (SCA off, {@code maxPenalty == 0}, no graded+positive category — which includes the default Hyperion categories, all
 * {@code FEEDBACK}/{@code INACTIVE}),
 * NO finding is penalising, so the gate stays silent and the oracle's accept is unchanged. This deliberately does NOT over-reject on non-graded findings.
 */
final class ScaPenaltyParity {

    /** Sentinel raw-category for findings whose real category {@code verify.sh} could not derive in POSIX (SARIF/GCC/…); treated as "any category of the producing tool". */
    static final String UNKNOWN_CATEGORY = SandboxBuildCommandService.SCA_UNKNOWN_CATEGORY;

    private ScaPenaltyParity() {
    }

    /**
     * @param exercise            the exercise whose SCA configuration governs grading (must be the persisted exercise, so its language and id resolve the default mappings)
     * @param persistedCategories the exercise's persisted SCA categories (read the same way production does, {@code findByExerciseId}); their state/penalty decide grading
     * @param solutionFindings    the {@code <TOOL>|<rawCategory>} findings {@code verify.sh} emitted for the SOLUTION build (the {@code HYPERION_SCA} payloads)
     * @return the distinct findings that production WOULD penalise (a graded, positively-penalised category); empty when production would deduct nothing
     */
    static List<String> penalisingFindings(ProgrammingExercise exercise, Set<StaticCodeAnalysisCategory> persistedCategories, List<String> solutionFindings) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
            return List.of();
        }
        // maxStaticCodeAnalysisPenalty defaults to 100 when null (mirrors calculateTotalPenalty); 0 disables the SCA penalty entirely.
        int maxPenalty = exercise.getMaxStaticCodeAnalysisPenalty() != null ? exercise.getMaxStaticCodeAnalysisPenalty() : 100;
        if (maxPenalty <= 0 || persistedCategories == null || persistedCategories.isEmpty() || solutionFindings == null || solutionFindings.isEmpty()) {
            return List.of();
        }
        List<StaticCodeAnalysisDefaultCategory> defaults = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(exercise.getProgrammingLanguage());
        if (defaults == null) {
            return List.of();
        }

        List<String> penalising = new ArrayList<>();
        for (String finding : solutionFindings) {
            int sep = finding.indexOf('|');
            if (sep < 0) {
                continue;
            }
            String tool = finding.substring(0, sep).trim();
            String rawCategory = finding.substring(sep + 1).trim();
            if (tool.isEmpty()) {
                continue;
            }
            if (isPenalising(tool, rawCategory, persistedCategories, defaults) && !penalising.contains(finding)) {
                penalising.add(finding);
            }
        }
        return penalising;
    }

    /**
     * Whether a single {@code (tool, rawCategory)} finding maps to a persisted category that is {@code GRADED} with a positive penalty — i.e. production would deduct points for
     * it.
     * Mirrors {@code findCategoryForIssue} (a persisted category matches a default category by name, and that default's {@code categoryMappings} contains
     * {@code (tool, category)}),
     * plus the {@code GRADED} + {@code penalty > 0} gate of {@code calculateStaticCodeAnalysisPenalty}.
     * <p>
     * For {@link #UNKNOWN_CATEGORY} findings (the tools whose category is not derived in POSIX), the finding is penalising iff the producing TOOL has ANY persisted graded,
     * positively-penalised category — the documented conservative fallback (a sound over-rejection rather than an unsound accept).
     */
    private static boolean isPenalising(String tool, String rawCategory, Set<StaticCodeAnalysisCategory> persistedCategories, List<StaticCodeAnalysisDefaultCategory> defaults) {
        boolean unknownCategory = UNKNOWN_CATEGORY.equals(rawCategory);
        for (StaticCodeAnalysisCategory category : persistedCategories) {
            if (category.getState() != CategoryState.GRADED) {
                continue;
            }
            // A GRADED category with no positive penalty deducts nothing (categoryFeedback.size() * penalty == 0), so it cannot pull the solution below 100%.
            if (category.getPenalty() == null || category.getPenalty() <= 0) {
                continue;
            }
            StaticCodeAnalysisDefaultCategory defaultMatch = defaults.stream().filter(d -> d.name().equals(category.getName())).findFirst().orElse(null);
            if (defaultMatch == null) {
                continue;
            }
            for (StaticCodeAnalysisDefaultCategory.CategoryMapping mapping : defaultMatch.categoryMappings()) {
                if (!mapping.tool().name().equalsIgnoreCase(tool)) {
                    continue;
                }
                // Category match (production semantics), or the conservative tool-wide match for an undetermined category.
                if (unknownCategory || mapping.category().equalsIgnoreCase(rawCategory)) {
                    return true;
                }
            }
        }
        return false;
    }
}
