package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.*;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.*;

/**
 * Service for handling Solution Entries of behavioral Test Cases.
 */
@Service
public class BehavioralTestCaseService {

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final TestwiseCoverageService testwiseCoverageService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public BehavioralTestCaseService(GitService gitService, RepositoryService repositoryService, TestwiseCoverageService testwiseCoverageService,
            ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseSolutionEntryRepository solutionEntryRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.testCaseRepository = testCaseRepository;
        this.testwiseCoverageService = testwiseCoverageService;
        this.solutionEntryRepository = solutionEntryRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
    }

    public List<ProgrammingExerciseSolutionEntry> generateBehavioralSolutionEntries(ProgrammingExercise programmingExercise) throws BehavioralSolutionEntryGenerationException {
        if (!programmingExercise.isTestwiseCoverageEnabled()) {
            throw new BehavioralSolutionEntryGenerationException("This feature is only supported for Java Exercises with active Testwise Coverage");
        }
        var testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        if (testCases.isEmpty()) {
            return null;
        }
        var gitDiffReport = programmingExercise.getGitDiffReport();
        if (gitDiffReport == null) {
            throw new BehavioralSolutionEntryGenerationException("Git-Diff Report has not been generated");
        }
        var coverageReport = testwiseCoverageService.getFullCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(programmingExercise).orElse(null);
        if (coverageReport == null) {
            throw new BehavioralSolutionEntryGenerationException("Testwise coverage report has not been generated");
        }
        var solutionRepoFiles = readSolutionRepo(programmingExercise);

        var blackboard = new BehavioralBlackboard(gitDiffReport, coverageReport, solutionRepoFiles);
        applyKnowledgeSources(blackboard);
        var solutionEntries = blackboard.getSolutionEntries();
        if (solutionEntries == null || solutionEntries.isEmpty()) {
            throw new BehavioralSolutionEntryGenerationException("No solution entry was generated");
        }
        solutionEntryRepository.saveAll(solutionEntries);
        return solutionEntries;
    }

    private void applyKnowledgeSources(BehavioralBlackboard blackboard) throws BehavioralSolutionEntryGenerationException {
        // Create knowledge sources (Turning the formatter off to make the code more readable)
        // @formatter:off
        List<BehavioralKnowledgeSource> behavioralKnowledgeSources = Arrays.asList(
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
        while (!done) {
            boolean didChanges = false;
            for (BehavioralKnowledgeSource behavioralKnowledgeSource : behavioralKnowledgeSources) {
                if (behavioralKnowledgeSource.executeCondition()) {
                    didChanges = behavioralKnowledgeSource.executeAction() || didChanges;
                }
            }
            done = !didChanges;
        }
    }

    private Map<String, String> readSolutionRepo(ProgrammingExercise programmingExercise) throws BehavioralSolutionEntryGenerationException {
        try {
            var solutionParticipationOptional = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
            if (solutionParticipationOptional.isEmpty()) {
                return Collections.emptyMap();
            }
            var solutionParticipation = solutionParticipationOptional.get();
            var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUrl(), true);

            gitService.resetToOriginHead(solutionRepo);
            gitService.pullIgnoreConflicts(solutionRepo);

            return repositoryService.getFilesWithContent(solutionRepo);
        }
        catch (GitAPIException e) {
            throw new BehavioralSolutionEntryGenerationException("Error while reading solution repository", e);
        }
    }
}
