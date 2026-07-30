package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/** Merges and validates source-isolated behavioral results separately from exact server-owned structural results. */
final class ProvenanceSeparatedBuilds {

    private ProvenanceSeparatedBuilds() {
    }

    static BuildSummary merge(BuildSummary behavioral, BuildSummary structural) {
        List<String> names = Stream.concat(behavioral.testNames().stream(), structural.testNames().stream()).toList();
        List<String> failedNames = Stream.concat(behavioral.testFailedNames().stream(), structural.testFailedNames().stream()).toList();
        List<AgentVerifyReport.TestFailureEvidence> evidence = Stream.concat(behavioral.failureEvidence().stream(), structural.failureEvidence().stream()).toList();
        int exitCode = behavioral.exitCode() != 0 ? behavioral.exitCode() : structural.exitCode();
        String diagnostic = "[behavior-isolated]\n" + behavioral.buildDiagnostic() + "\n[trusted-structural]\n" + structural.buildDiagnostic();
        return new BuildSummary(behavioral.tests() + structural.tests(), behavioral.failures() + structural.failures(), exitCode, behavioral.timedOut() || structural.timedOut(),
                names, failedNames, evidence, behavioral.scaFindings(), diagnostic);
    }

    static boolean validate(BuildSummary behavioralSolution, BuildSummary behavioralTemplate, @Nullable BuildSummary structuralSolution, @Nullable BuildSummary structuralTemplate,
            Set<String> expectedStructuralNames, List<String> reasons) {
        boolean sound = true;
        Set<String> expected = expectedStructuralNames.stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toSet());
        Set<String> solutionBehaviorNames = normalizedNames(behavioralSolution);
        Set<String> templateBehaviorNames = normalizedNames(behavioralTemplate);
        Set<String> collisions = solutionBehaviorNames.stream().filter(expected::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!collisions.isEmpty()) {
            reasons.add("Behavioral tests impersonate server-seeded structural result names: " + collisions
                    + ". Rename the behavioral tests; structural grading provenance cannot be claimed by candidate-authored code.");
            sound = false;
        }
        if (!solutionBehaviorNames.equals(templateBehaviorNames) || behavioralSolution.tests() != behavioralTemplate.tests()) {
            reasons.add("The source-isolated behavioral lane ran different tests for solution and template. Both assignments must execute the same behavioral test set.");
            sound = false;
        }
        if (expected.isEmpty()) {
            if (structuralSolution != null || structuralTemplate != null) {
                reasons.add("The trusted structural lane ran without an authoritative server-seeded structural bundle.");
                return false;
            }
            return sound;
        }
        if (structuralSolution == null || structuralTemplate == null) {
            reasons.add("The authoritative structural bundle exists, but its trusted structural lane did not run.");
            return false;
        }
        Set<String> solutionStructuralNames = normalizedNames(structuralSolution);
        Set<String> templateStructuralNames = normalizedNames(structuralTemplate);
        if (!solutionStructuralNames.equals(expected) || structuralSolution.tests() != expected.size()) {
            reasons.add("The trusted structural solution lane did not report exactly the server-seeded checks. Expected " + expected + " but got " + solutionStructuralNames + ".");
            sound = false;
        }
        if (!templateStructuralNames.equals(expected) || structuralTemplate.tests() != expected.size()) {
            reasons.add("The trusted structural template lane did not report exactly the server-seeded checks. Expected " + expected + " but got " + templateStructuralNames + ".");
            sound = false;
        }
        return sound;
    }

    private static Set<String> normalizedNames(BuildSummary summary) {
        return summary.testNames().stream().map(ProblemStatementBindingChecker::normalizeTestName).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
