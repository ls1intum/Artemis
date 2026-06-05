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
 * so the SCA tools (SpotBugs/Checkstyle/PMD, ruff/clippy/eslint/…) execute in BOTH. But their reports carry no JUnit {@code <testcase>}, so the differential oracle (which decides
 * only from JUnit results) is blind to them while production folds an SCA penalty into the score ({@code ProgrammingExerciseGradingService.calculateTotalPenalty}). A reference
 * solution with graded SCA violations was therefore ACCEPTED by the oracle yet grades below 100% for a student. The verifier now collects the SCA report files and parses them with
 * the production {@code ReportParser} (so each finding carries the REAL derived category, including SARIF/GCC via the production categorizers), then asks this class which of those
 * findings production would actually penalise.
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

    /**
     * One static-code-analysis finding the verifier extracted from a collected SCA report: the producing tool name (matching a {@link StaticCodeAnalysisTool} enum constant) and
     * the
     * REAL derived issue category from the production {@code ReportParser} (including SARIF/GCC categorizers). Used in place of the older {@code <TOOL>|<rawCategory>} string form
     * so
     * there is no string-splitting and no sentinel "undetermined" category — the production parser always yields a concrete category.
     *
     * @param tool     the producing tool name (e.g. {@code SPOTBUGS}, {@code RUFF})
     * @param category the real derived issue category (e.g. {@code STYLE}, {@code javadoc})
     */
    record ScaFinding(String tool, String category) {
    }

    private ScaPenaltyParity() {
    }

    /**
     * @param exercise            the exercise whose SCA configuration governs grading (must be the persisted exercise, so its language and id resolve the default mappings)
     * @param persistedCategories the exercise's persisted SCA categories (read the same way production does, {@code findByExerciseId}); their state/penalty decide grading
     * @param solutionFindings    the SCA findings the verifier extracted from the SOLUTION build's collected reports (tool + real derived category)
     * @return the distinct findings that production WOULD penalise (a graded, positively-penalised category); empty when production would deduct nothing
     */
    static List<ScaFinding> penalisingFindings(ProgrammingExercise exercise, Set<StaticCodeAnalysisCategory> persistedCategories, List<ScaFinding> solutionFindings) {
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

    /**
     * Whether a single {@code (tool, category)} finding maps to a persisted category that is {@code GRADED} with a positive penalty — i.e. production would deduct points for it.
     * Mirrors {@code findCategoryForIssue} (a persisted category matches a default category by name, and that default's {@code categoryMappings} contains
     * {@code (tool, category)}),
     * plus the {@code GRADED} + {@code penalty > 0} gate of {@code calculateStaticCodeAnalysisPenalty}.
     */
    private static boolean isPenalising(String tool, String category, Set<StaticCodeAnalysisCategory> persistedCategories, List<StaticCodeAnalysisDefaultCategory> defaults) {
        for (StaticCodeAnalysisCategory persisted : persistedCategories) {
            if (persisted.getState() != CategoryState.GRADED) {
                continue;
            }
            // A GRADED category with no positive penalty deducts nothing (categoryFeedback.size() * penalty == 0), so it cannot pull the solution below 100%.
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
