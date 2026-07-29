package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService.VerificationInfrastructureException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Executes model-authored contract witnesses against pristine reference and starter builds. */
final class ContractWitnessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ContractWitnessEvaluator.class);

    private ContractWitnessEvaluator() {
    }

    static List<ContractWitnessOutcome> evaluate(InteractiveSandbox sandbox, String sessionId, Long exerciseId, Map<String, String> producedTestsFiles,
            List<ContractWitness> candidates, Runnable restoreCandidate, Runnable seedVerifyScript, Supplier<BuildSummary> solutionBuild, Supplier<BuildSummary> templateBuild) {
        Set<String> duplicateNames = candidates.stream().collect(Collectors.groupingBy(ContractWitness::testName, Collectors.counting())).entrySet().stream()
                .filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        List<ContractWitnessOutcome> outcomes = new ArrayList<>();
        List<ContractWitness> runnable = new ArrayList<>();
        for (ContractWitness candidate : candidates) {
            if (duplicateNames.contains(candidate.testName()) || ContractWitnessProbe.collidesWithExistingTest(candidate, producedTestsFiles)) {
                outcomes.add(new ContractWitnessOutcome(candidate, ContractWitnessOutcome.Disposition.INCONCLUSIVE,
                        "The witness test name collides with another proposal or an existing graded test."));
            }
            else {
                runnable.add(candidate);
            }
        }
        if (runnable.isEmpty()) {
            return List.copyOf(outcomes);
        }
        Optional<Map.Entry<String, String>> host = ContractWitnessProbe.host(producedTestsFiles);
        if (host.isEmpty()) {
            outcomes.addAll(inconclusive(runnable, "No assertion-based test source could host the witness probe."));
            return List.copyOf(outcomes);
        }
        String probePath = ContractWitnessProbe.probePath(host.get().getKey(), producedTestsFiles.keySet());
        if (probePath == null) {
            outcomes.addAll(inconclusive(runnable, "The contract-witness probe path was unavailable."));
            return List.copyOf(outcomes);
        }
        try {
            for (ContractWitness candidate : runnable) {
                restoreCandidate.run();
                String probeSource = ContractWitnessProbe.buildProbeSource(host.get().getValue(), List.of(candidate));
                String workspacePath = GenerationWorkspaceService.directoryFor(RepositoryType.TESTS) + "/" + probePath;
                sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(Map.of(workspacePath, probeSource), Map.of()));
                seedVerifyScript.run();
                BuildSummary solution = solutionBuild.get();
                if (ContractWitnessProbe.executed(candidate, solution.testNames()) && ContractWitnessProbe.failed(candidate, solution.testFailedNames()) && !solution.timedOut()) {
                    outcomes.add(outcome(candidate, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, solution));
                    continue;
                }
                boolean solutionPassedWitness = ContractWitnessProbe.executed(candidate, solution.testNames())
                        && !ContractWitnessProbe.failed(candidate, solution.testFailedNames()) && !solution.timedOut() && solution.exitCode() == 0 && solution.failures() == 0;
                if (!solutionPassedWitness) {
                    log.info("Contract-witness {} for exercise {} was rejected before starter comparison: solution tests={}, failures={}, exit={}; diagnostic={}",
                            candidate.testName(), exerciseId, solution.tests(), solution.failures(), solution.exitCode(), solution.buildDiagnostic());
                    outcomes.add(new ContractWitnessOutcome(candidate, ContractWitnessOutcome.Disposition.INCONCLUSIVE, solution.buildDiagnostic()));
                    continue;
                }
                BuildSummary template = templateBuild.get();
                if (ContractWitnessProbe.executed(candidate, template.testNames()) && ContractWitnessProbe.failed(candidate, template.testFailedNames()) && !template.timedOut()) {
                    outcomes.add(outcome(candidate, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, template));
                }
                else if (ContractWitnessProbe.executed(candidate, template.testNames()) && !ContractWitnessProbe.failed(candidate, template.testFailedNames())
                        && !template.timedOut()) {
                    outcomes.add(new ContractWitnessOutcome(candidate, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_NOT_FAILED, ""));
                }
                else {
                    log.info("Contract-witness {} for exercise {} passed the solution but did not execute and fail at the starter seam: starter tests={}, failures={}, exit={}; "
                            + "diagnostic={}", candidate.testName(), exerciseId, template.tests(), template.failures(), template.exitCode(), template.buildDiagnostic());
                    outcomes.add(new ContractWitnessOutcome(candidate, ContractWitnessOutcome.Disposition.INCONCLUSIVE, template.buildDiagnostic()));
                }
            }
            Map<ContractWitnessOutcome.Disposition, Long> counts = outcomes.stream().collect(Collectors.groupingBy(ContractWitnessOutcome::disposition, Collectors.counting()));
            log.info("Contract-witness probe for exercise {}: {} proposal outcomes {}", exerciseId, outcomes.size(), counts);
            return List.copyOf(outcomes);
        }
        catch (VerificationInfrastructureException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new VerificationInfrastructureException("The contract-witness probe could not restore the verified candidate", exception);
        }
        finally {
            try {
                restoreCandidate.run();
            }
            catch (RuntimeException exception) {
                throw new VerificationInfrastructureException("The contract-witness probe could not restore the verified candidate", exception);
            }
        }
    }

    static List<ContractWitnessOutcome> inconclusive(List<ContractWitness> candidates, String diagnostic) {
        return candidates.stream().map(candidate -> new ContractWitnessOutcome(candidate, ContractWitnessOutcome.Disposition.INCONCLUSIVE, diagnostic)).toList();
    }

    private static ContractWitnessOutcome outcome(ContractWitness candidate, ContractWitnessOutcome.Disposition disposition, BuildSummary summary) {
        return new ContractWitnessOutcome(candidate, disposition, ContractWitnessProbe.failureDiagnostic(candidate, summary));
    }
}
