package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageChecks;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;

/** Evaluates official stage boundaries against current structural authority and mechanical results. */
final class StageGateEvaluator {

    private StageGateEvaluator() {
    }

    record Result(StageCheckResult result, boolean reused) {
    }

    /**
     * Evaluates one stage's exit gate: reuses the tools' cached passing check when nothing has changed since it ran (see {@link SandboxAgentTools#reuseCachedPassingCheck}), and
     * otherwise delegates to {@link StageChecks}. The controller receives a typed result and owns sequencing, budgets and re-entry.
     */
    static Result evaluate(StageChecks stageCheckService, GenerationStage stage, SandboxAgentTools baseTools, InteractiveSandbox sandbox, String sessionId,
            GenerationInput exercise, Map<String, String> seedTestsFiles, @Nullable AgentVerifyReport lastTestsReport) {
        if (stage == GenerationStage.TESTS) {
            // The TESTS stage may repair solution/template after its in-loop check. Re-seed first and always run the official differential against that final artifact state;
            // a cached pass was computed with the pre-TESTS structural oracle and cannot prove the grading plan covers newly changed structural names.
            try {
                baseTools.refreshStructuralOracle();
            }
            catch (RuntimeException e) {
                return new Result(StageCheckResult.failed("The server could not materialize the approved student-created structural contract: " + e.getMessage()
                        + ". Keep every approved student-created type public in the solution and absent from the template; do not edit the server-generated structural test assets."),
                        false);
            }
            // Only this end-of-attempt gate may correct an unsatisfiable given ownership: the correction is irreversible, so it must not fire on a half-written solution.
            Optional<String> ownershipCorrection = stageCheckService.correctGivenOwnership(sandbox, sessionId);
            StageCheckResult result = stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, baseTools.seededStructuralTests());
            result = classifyAssessment(result, baseTools.seededStructuralTests());
            if (ownershipCorrection.isPresent()) {
                result = new StageCheckResult(result.outcome(), ownershipCorrection.get() + "\n" + result.observation(), result.report());
            }
            return new Result(result, false);
        }
        Optional<StageCheckResult> reused = baseTools.reuseCachedPassingCheck(stage);
        if (reused.isPresent()) {
            return new Result(reused.get(), true);
        }
        return new Result(stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, baseTools.seededStructuralTests()), false);
    }

    /** An unchanged starter that passes every declared student-work test is an executed assessment counterexample, even without output comparisons. */
    static StageCheckResult classifyAssessment(StageCheckResult result, SeededStructuralTests seeded) {
        AgentVerifyReport report = result.report();
        if (report == null || !report.solutionPassed() || !report.templateCompiled() || report.solutionTests() == 0 || report.templateTests() != report.solutionTests()) {
            return result;
        }
        List<String> studentTests = report.exactTestNames().stream().filter(name -> !seeded.testNames().contains(name) && !report.preservationTestNames().contains(name)).toList();
        if (studentTests.isEmpty() || !report.templateWronglyPassing().containsAll(studentTests)) {
            return result;
        }
        return StageCheckResult.redesignRequired("All " + studentTests.size()
                + " student-work tests pass on the unchanged template. The executed assessment cannot distinguish the missing learner work from the reference solution. "
                + "Reconsider how the contract exposes the student-owned operations; tests that perform those operations themselves only verify supplied support.\n"
                + result.observation(), report);
    }

    /** A line that states something worked cannot be the reason a gate failed, however early it appears in the report. */
    private static boolean readsAsPassing(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("tests pass.") || lower.contains("all required gradable tests fail") || lower.startsWith("the differential passed");
    }

    static String firstLineBounded(@Nullable String text, int maxChars) {
        if (text == null) {
            return "";
        }
        // A header line ending in ':' carries no information on its own, so fold the first content line in and the instructor-visible event actually says what went wrong.
        String[] lines = text.strip().split("\n");
        String firstLine = lines[0].strip();
        if (firstLine.endsWith(":")) {
            // The report leads with whichever lane it describes first, and that lane may be the one that succeeded: a template that wrongly passes produces a report opening
            // "Solution: 9/9 tests pass." Folding that in makes a failure read as a success and tells the instructor nothing, so skip past the lines that state something worked.
            String candidate = "";
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (candidate.isEmpty()) {
                    candidate = line;
                }
                if (!readsAsPassing(line)) {
                    candidate = line;
                    break;
                }
            }
            if (!candidate.isEmpty()) {
                firstLine = firstLine + " " + candidate;
            }
        }
        if (firstLine.codePointCount(0, firstLine.length()) <= maxChars) {
            return firstLine;
        }
        int end = firstLine.offsetByCodePoints(0, maxChars);
        return firstLine.substring(0, end) + "…";
    }

    /**
     * Models occasionally return the complete requested specification in their final text instead of invoking {@code write_file}. Preserve that authored content verbatim and
     * still subject it to the normal mechanical and semantic gates rather than spending a full retry merely moving the same bytes into the workspace.
     */
    static void materializeReturnedSpecification(SandboxAgentTools baseTools, InteractiveSandbox sandbox, String sessionId, @Nullable String finalMessage) {
        if (!Objects.requireNonNullElse(GenerationWorkspace.readRootArtifact(sandbox, sessionId, "SPEC.md"), "").isBlank() || finalMessage == null) {
            return;
        }
        int heading = finalMessage.indexOf("# SPEC.md");
        if (heading < 0) {
            return;
        }
        String specification = finalMessage.substring(heading).strip();
        if (specification.endsWith("```")) {
            specification = specification.substring(0, specification.length() - 3).stripTrailing();
        }
        String result = baseTools.writeFile("SPEC.md", specification);
        if (result != null && !result.startsWith("ERROR")) {
            LoggerFactory.getLogger(StageGateEvaluator.class).info("Materialized SPEC.md from the agent's returned artifact before applying the normal specification gates");
        }
    }

}
