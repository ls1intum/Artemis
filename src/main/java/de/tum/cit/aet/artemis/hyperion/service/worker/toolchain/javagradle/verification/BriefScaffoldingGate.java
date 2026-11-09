package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.BriefLearningGoals;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.BriefLearningGoals.ConstructorBaseline;

/** Checks source-owned starting-state requirements against declarations rather than a candidate's coverage claims. */
public final class BriefScaffoldingGate {

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

    /**
     * @param complete whether the declarations and their learner owner were unambiguous
     * @param findings proven violations, or the reason the starting state could not be checked
     */
    public record Check(boolean complete, List<String> findings) {

        public Check {
            findings = List.copyOf(findings);
        }
    }

    private record SourceType(String path, JavaClass declaration) {
    }

    private BriefScaffoldingGate() {
    }

    /**
     * @param goals         the immutable requirements extracted from the brief alone
     * @param specification the proposed specification
     * @return whether the proposed starter preserves the explicit initial constructor state
     */
    public static Check checkSpecification(BriefLearningGoals goals, String specification) {
        List<BriefLearningGoals.Goal> required = constructorGoals(goals);
        boolean learnerTestsRequired = goals.goals().stream().anyMatch(goal -> goal.learnerArtifact() == BriefLearningGoals.LearnerArtifact.TEST_SUITE);
        if (required.isEmpty() && !learnerTestsRequired) {
            return new Check(true, List.of());
        }
        List<StageChecks.DesignRow> rows = StageChecks.designTableRows(specification);
        Set<String> types = rows.stream().map(StageChecks.DesignRow::type).collect(Collectors.toSet());
        ApprovedStructuralContract.ParseResult parsed = ApprovedStructuralContract.parse(specification, types, Set.copyOf(StageChecks.specStudentCreatedTypes(specification)));
        if (!parsed.valid()) {
            return new Check(false, List.of("The instructor's starting-state requirement cannot be checked against an invalid Java Public API: " + parsed.errors()));
        }
        Set<String> starterTypes = rows.stream().filter(row -> "given".equals(row.status()) || "stubbed".equals(row.status())).map(StageChecks.DesignRow::type)
                .collect(Collectors.toSet());
        Set<String> learnerOwners = rows.stream().filter(row -> "stubbed".equals(row.status()) || parsed.contract().studentCreatedMemberOwners().contains(row.type()))
                .map(StageChecks.DesignRow::type).filter(starterTypes::contains).collect(Collectors.toSet());
        List<String> findings = new ArrayList<>();
        if (learnerTestsRequired) {
            Set<String> writableTypes = rows.stream().filter(row -> "stubbed".equals(row.status()) || "student-creates".equals(row.status())).map(StageChecks.DesignRow::type)
                    .collect(Collectors.toSet());
            if (parsed.contract().learnerTestOwners(writableTypes).isEmpty()) {
                findings.add("The brief assigns test authoring to the learner, but the Public API contains no learner-owned JUnit test method. "
                        + "Declare the learner's test methods in a stubbed or student-created type, or mark those methods @studentCreates. "
                        + "A fully supplied test class and implementation-only grading do not assess learner-written tests. Preserve every original goal and define its grading seam.");
            }
        }
        for (BriefLearningGoals.Goal goal : required) {
            Set<String> targets = targets(goal, types, learnerOwners);
            if (targets.size() > 1) {
                return new Check(false, List.of("The starting-state requirement [" + goal.id() + "] cannot be assigned unambiguously to one existing learner-owned class."));
            }
            if (targets.isEmpty() || !starterTypes.contains(targets.iterator().next()) || !parsed.contract().hasConstructorlessStarter(targets.iterator().next())) {
                findings.add("Starting-state mismatch — [" + goal.id() + "] the brief requires \"" + goal.quote()
                        + "\". The learner must add the first explicit constructor to an existing class. A supplied constructor, a constructor stub, or an overload of an existing "
                        + "constructor changes that starting point. Keep the supplied class constructorless and mark the learner-created constructor with @studentCreates in Public API.");
            }
        }
        return new Check(true, findings);
    }

    /**
     * @param goals         the retained source requirements, when available
     * @param specification the frozen specification
     * @param template      the exact generated starter sources
     * @param solution      the exact generated reference sources
     * @return persistent review findings for a changed or unverifiable starting state; mechanical validity is retained
     */
    public static List<SpecFidelityReport.Finding> artifactFindings(@Nullable BriefLearningGoals goals, @Nullable String specification, Map<String, String> template,
            Map<String, String> solution) {
        if (goals == null || goals.goals().stream()
                .allMatch(goal -> goal.constructorBaseline() == ConstructorBaseline.UNSPECIFIED && goal.learnerArtifact() != BriefLearningGoals.LearnerArtifact.TEST_SUITE)) {
            return List.of();
        }
        String frozenSpecification = specification == null ? "" : specification;
        Check planned = checkSpecification(goals, frozenSpecification);
        if (!planned.complete()) {
            return SpecFidelityReport.qualityReviewUnavailable(String.join("\n", planned.findings())).findings();
        }
        if (!planned.findings().isEmpty()) {
            return planned.findings().stream().map(detail -> new SpecFidelityReport.Finding(SpecFidelityReport.Kind.SPECIFICATION_REVIEW_FINDING,
                    "The compiled specification changes the instructor's required learner work", detail)).toList();
        }
        boolean learnerTestsRequired = goals.goals().stream().anyMatch(goal -> goal.learnerArtifact() == BriefLearningGoals.LearnerArtifact.TEST_SUITE);
        if (learnerTestsRequired && solution.entrySet().stream().filter(entry -> entry.getKey().endsWith(".java"))
                .noneMatch(entry -> !JavaSourceInspector.studentTestVariants(entry.getValue(), template.getOrDefault(entry.getKey(), "")).isEmpty())) {
            return SpecFidelityReport.qualityReviewUnavailable("The brief requires learner-authored tests, but no changed executable JUnit test body could be established "
                    + "between the starter and reference. Supplied instructor assertions cannot satisfy this goal; confirm learner ownership and runtime grading before study use.")
                    .findings();
        }
        if (constructorGoals(goals).isEmpty()) {
            return List.of();
        }
        try {
            Map<String, SourceType> starterTypes = sourceTypes(template);
            Map<String, SourceType> referenceTypes = sourceTypes(solution);
            List<StageChecks.DesignRow> rows = StageChecks.designTableRows(frozenSpecification);
            Set<String> names = rows.stream().map(StageChecks.DesignRow::type).collect(Collectors.toSet());
            Set<String> learnerOwners = rows.stream().filter(row -> "stubbed".equals(row.status())).map(StageChecks.DesignRow::type).collect(Collectors.toSet());
            ApprovedStructuralContract.ParseResult parsed = ApprovedStructuralContract.parse(frozenSpecification, names,
                    Set.copyOf(StageChecks.specStudentCreatedTypes(frozenSpecification)));
            learnerOwners.addAll(parsed.contract().studentCreatedMemberOwners());
            Set<String> existingTypes = rows.stream().filter(row -> "given".equals(row.status()) || "stubbed".equals(row.status())).map(StageChecks.DesignRow::type)
                    .collect(Collectors.toSet());
            learnerOwners.retainAll(existingTypes);
            List<SpecFidelityReport.Finding> findings = new ArrayList<>();
            for (BriefLearningGoals.Goal goal : constructorGoals(goals)) {
                String target = targets(goal, names, learnerOwners).iterator().next();
                SourceType starter = starterTypes.get(target);
                SourceType reference = referenceTypes.get(target);
                if (starter == null || !starter.declaration().getConstructors().isEmpty() || reference == null || reference.declaration().getConstructors().isEmpty()) {
                    SpecFidelityReport.Location location = starter == null ? null
                            : new SpecFidelityReport.Location(SpecFidelityReport.Location.Repository.TEMPLATE, starter.path(), Math.max(1, starter.declaration().getLineNumber()),
                                    Math.max(1, starter.declaration().getLineNumber()));
                    findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "Preserve the constructorless starting class for [" + goal.id() + "]",
                            "The brief requires \"" + goal.quote() + "\". " + target
                                    + " must exist in the starter without any explicit constructor; the learner creates its constructor in the solution. Do not supply a stub or a private overload instead.",
                            location));
                }
            }
            return List.copyOf(findings);
        }
        catch (RuntimeException error) {
            return SpecFidelityReport.qualityReviewUnavailable("The generated declarations could not establish the instructor's required starting state.").findings();
        }
    }

    private static List<BriefLearningGoals.Goal> constructorGoals(BriefLearningGoals goals) {
        return goals.goals().stream().filter(goal -> goal.constructorBaseline() == ConstructorBaseline.NO_EXPLICIT_CONSTRUCTORS).toList();
    }

    private static Set<String> targets(BriefLearningGoals.Goal goal, Set<String> names, Set<String> learnerOwners) {
        Set<String> named = JAVA_IDENTIFIER.matcher(goal.quote()).results().map(MatchResult::group).filter(names::contains).collect(Collectors.toSet());
        return named.isEmpty() ? learnerOwners : named;
    }

    private static Map<String, SourceType> sourceTypes(Map<String, String> files) {
        Map<String, SourceType> result = new LinkedHashMap<>();
        files.entrySet().stream().filter(entry -> entry.getKey().endsWith(".java")).forEach(entry -> {
            JavaProjectBuilder builder = new JavaProjectBuilder();
            builder.addSource(new StringReader(entry.getValue()));
            for (JavaClass type : builder.getClasses()) {
                if (result.putIfAbsent(type.getSimpleName(), new SourceType(entry.getKey(), type)) != null) {
                    throw new IllegalArgumentException("Ambiguous source type");
                }
            }
        });
        return result;
    }
}
