package de.tum.cit.aet.artemis.programming.service.hestia.behavioral;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.connectors.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.cit.aet.artemis.programming.service.hestia.TestwiseCoverageService;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.AddUncoveredLinesAsPotentialCodeBlocks;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.BehavioralKnowledgeSource;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.CombineChangeBlocks;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.CreateCommonChangeBlocks;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.CreateSolutionEntries;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.DropRemovedGitDiffEntries;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.ExtractChangedLines;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.ExtractCoveredLines;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.FindCommonLines;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.GroupGitDiffAndCoverageEntriesByFilePathAndTestCase;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource.InsertFileContents;

/**
 * Service for handling Solution Entries of behavioral Test Cases.
 */
@Profile(PROFILE_CORE)
@Service
public class BehavioralTestCaseService {

    private static final Logger log = LoggerFactory.getLogger(BehavioralTestCaseService.class);

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TestwiseCoverageService testwiseCoverageService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private final ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public BehavioralTestCaseService(GitService gitService, RepositoryService repositoryService, TestwiseCoverageService testwiseCoverageService,
            ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseSolutionEntryRepository solutionEntryRepository,
            ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.testCaseRepository = testCaseRepository;
        this.testwiseCoverageService = testwiseCoverageService;
        this.solutionEntryRepository = solutionEntryRepository;
        this.programmingExerciseGitDiffReportService = programmingExerciseGitDiffReportService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
    }

    /**
     * Generates the solution entries for all behavioral test cases of a programming exercise.
     * This uses the git-diff report, the testwise coverage, and the test cases of the programming exercise and
     * requires them to exist.
     * Therefore, this method also requires the exercise to have testwiseCoverageEnabled set to true.
     *
     * @param programmingExercise The programming exercise
     * @return The new behavioral solution entries
     * @throws BehavioralSolutionEntryGenerationException If there was an error while generating the solution entries
     */
    public List<ProgrammingExerciseSolutionEntry> generateBehavioralSolutionEntries(ProgrammingExercise programmingExercise) throws BehavioralSolutionEntryGenerationException {
        if (!programmingExercise.getBuildConfig().isTestwiseCoverageEnabled()) {
            throw new BehavioralSolutionEntryGenerationException("This feature is only supported for Java Exercises with active Testwise Coverage");
        }
        var testCases = testCaseRepository.findByExerciseIdWithSolutionEntriesAndActive(programmingExercise.getId(), true);
        if (testCases.isEmpty()) {
            throw new BehavioralSolutionEntryGenerationException("Test cases have not been received yet");
        }
        var gitDiffReport = programmingExerciseGitDiffReportService.getOrCreateReportOfExercise(programmingExercise);
        if (gitDiffReport == null) {
            throw new BehavioralSolutionEntryGenerationException("Git-Diff Report has not been generated");
        }

        var coverageReport = testwiseCoverageService.getFullCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(programmingExercise.getId()).orElse(null);
        if (coverageReport == null) {
            throw new BehavioralSolutionEntryGenerationException("Testwise coverage report has not been generated");
        }
        log.info("Generating the behavioral solution entries for programming exercise {}", programmingExercise.getId());

        var solutionRepoFiles = readSolutionRepo(programmingExercise);

        var blackboard = new BehavioralBlackboard(gitDiffReport, coverageReport, solutionRepoFiles);
        applyKnowledgeSources(blackboard);
        var newSolutionEntries = blackboard.getSolutionEntries();
        if (newSolutionEntries == null || newSolutionEntries.isEmpty()) {
            throw new BehavioralSolutionEntryGenerationException("No solution entry was generated");
        }
        // Remove temporary id before saving
        for (ProgrammingExerciseSolutionEntry solutionEntry : newSolutionEntries) {
            solutionEntry.setId(null);
        }
        newSolutionEntries = solutionEntryRepository.saveAll(newSolutionEntries);

        // Get all old solution entries
        var oldSolutionEntries = newSolutionEntries.stream().map(ProgrammingExerciseSolutionEntry::getTestCase)
                .map(testCase1 -> testCases.stream().filter(testCase2 -> Objects.equals(testCase1.getId(), testCase2.getId())).findFirst().orElse(null)).filter(Objects::nonNull)
                .flatMap(testCase -> testCase.getSolutionEntries().stream()).distinct().toList();

        // Save new solution entries
        newSolutionEntries = solutionEntryRepository.saveAll(newSolutionEntries);

        // Delete old solution entries
        solutionEntryRepository.deleteAll(oldSolutionEntries);

        log.info("{} behavioral solution entries for programming exercise {} have been generated", newSolutionEntries.size(), programmingExercise.getId());
        return newSolutionEntries;
    }

    /**
     * Utilizing the blackboard pattern this method creates the behavioral solution entries step by step.
     * Look at the specific KnowledgeSources to learn more.
     *
     * @param blackboard The blackboard containing the base information
     * @throws BehavioralSolutionEntryGenerationException If there was an error while generating the solution entries
     */
    private void applyKnowledgeSources(BehavioralBlackboard blackboard) throws BehavioralSolutionEntryGenerationException {
        // Create knowledge sources (Turning the formatter off to make the code more readable)
        // @formatter:off
        List<BehavioralKnowledgeSource> behavioralKnowledgeSources = Arrays.asList(
            new DropRemovedGitDiffEntries(blackboard),
            new GroupGitDiffAndCoverageEntriesByFilePathAndTestCase(blackboard),
            new ExtractCoveredLines(blackboard),
            new ExtractChangedLines(blackboard),
            new FindCommonLines(blackboard),
            new CreateCommonChangeBlocks(blackboard),
            new InsertFileContents(blackboard),
            new AddUncoveredLinesAsPotentialCodeBlocks(blackboard),
            new CombineChangeBlocks(blackboard),
            new CreateSolutionEntries(blackboard)
        );
        // @formatter:on

        boolean done = false;
        int iterations = 0;
        while (!done) {
            boolean didChanges = false;
            for (BehavioralKnowledgeSource behavioralKnowledgeSource : behavioralKnowledgeSources) {
                if (behavioralKnowledgeSource.executeCondition()) {
                    log.debug("Executing knowledge source {}", behavioralKnowledgeSource.getClass().getSimpleName());
                    didChanges = behavioralKnowledgeSource.executeAction() || didChanges;
                }
            }
            done = !didChanges;
            iterations++;
            // Safeguard to prevent an infinite loop
            if (iterations >= 200) {
                throw new BehavioralSolutionEntryGenerationException("The creation of the solution entries got stuck and was cancelled");
            }
        }
    }

    /**
     * Reads the contents of all files in the solution repository and returns them mapped with the file path as the key.
     *
     * @param programmingExercise The programming exercise
     * @return The file path of each file mapped to their contents
     * @throws BehavioralSolutionEntryGenerationException If there was an error while reading the solution repository
     */
    private Map<String, String> readSolutionRepo(ProgrammingExercise programmingExercise) throws BehavioralSolutionEntryGenerationException {
        try {
            log.debug("Reading the contents of the solution repository");
            var solutionParticipationOptional = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
            if (solutionParticipationOptional.isEmpty()) {
                return Collections.emptyMap();
            }
            var solutionParticipation = solutionParticipationOptional.get();
            var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUri(), true);

            gitService.resetToOriginHead(solutionRepo);
            gitService.pullIgnoreConflicts(solutionRepo);

            return repositoryService.getFilesContentFromWorkingCopy(solutionRepo);
        }
        catch (GitAPIException e) {
            throw new BehavioralSolutionEntryGenerationException("Error while reading solution repository", e);
        }
    }
}
