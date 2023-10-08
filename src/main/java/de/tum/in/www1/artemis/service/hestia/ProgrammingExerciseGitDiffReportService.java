package de.tum.in.www1.artemis.service.hestia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * The service handling ProgrammingExerciseGitDiffReport and their ProgrammingExerciseGitDiffEntries.
 */
@Service
public class ProgrammingExerciseGitDiffReportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportService.class);

    private final GitService gitService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final FileService fileService;

    private final Pattern gitDiffLinePattern = Pattern.compile("@@ -(?<previousLine>\\d+)(,(?<previousLineCount>\\d+))? \\+(?<newLine>\\d+)(,(?<newLineCount>\\d+))? @@");

    public ProgrammingExerciseGitDiffReportService(GitService gitService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, FileService fileService) {
        this.gitService = gitService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.fileService = fileService;
    }

    /**
     * Updates the ProgrammingExerciseGitDiffReport of a programming exercise.
     * If there were no changes since the last report was created this will not do anything.
     * If there were changes to at least one of the repositories a new report will be created.
     * This method should not be called twice for the same programming exercise at the same time, as this will result in
     * the creation of 2 reports. See https://github.com/ls1intum/Artemis/pull/4893 for more information about it.
     *
     * @param programmingExercise The programming exercise
     * @return The git-diff report for the given programming exercise
     */
    public ProgrammingExerciseGitDiffReport updateReport(ProgrammingExercise programmingExercise) {
        var templateParticipationOptional = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
        var solutionParticipationOptional = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
        if (templateParticipationOptional.isEmpty() || solutionParticipationOptional.isEmpty()) {
            return null;
        }
        var templateParticipation = templateParticipationOptional.get();
        var solutionParticipation = solutionParticipationOptional.get();

        var templateSubmissionOptional = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(templateParticipation.getId());
        var solutionSubmissionOptional = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(solutionParticipation.getId());
        if (templateSubmissionOptional.isEmpty() || solutionSubmissionOptional.isEmpty()) {
            return null;
        }
        var templateSubmission = templateSubmissionOptional.get();
        var solutionSubmission = solutionSubmissionOptional.get();

        var templateHash = templateSubmission.getCommitHash();
        var solutionHash = solutionSubmission.getCommitHash();
        var existingReport = this.getReportOfExercise(programmingExercise);
        if (existingReport != null && canUseExistingReport(existingReport, templateHash, solutionHash)) {
            return existingReport;
        }

        try {
            var newReport = generateReport(templateParticipation, solutionParticipation);
            newReport.setTemplateRepositoryCommitHash(templateHash);
            newReport.setSolutionRepositoryCommitHash(solutionHash);
            newReport.setProgrammingExercise(programmingExercise);
            // Delete any old report first
            if (existingReport != null) {
                programmingExerciseGitDiffReportRepository.delete(existingReport);
            }
            newReport = programmingExerciseGitDiffReportRepository.save(newReport);
            programmingExerciseRepository.save(programmingExercise);
            return newReport;
        }
        catch (GitAPIException | IOException e) {
            log.error("Exception while generating git diff report", e);
            throw new InternalServerErrorException("Error while generating git-diff: " + e.getMessage());
        }
    }

    /**
     * Obtain the {@link ProgrammingExerciseGitDiffReport} of a programming exercise.
     * Contains proper error handling in case more than one report exists.
     *
     * @param programmingExercise The programming exercise
     * @return The report or null if none exists
     */
    private ProgrammingExerciseGitDiffReport getReportOfExercise(ProgrammingExercise programmingExercise) {
        var reports = programmingExerciseGitDiffReportRepository.findByProgrammingExerciseId(programmingExercise.getId());
        if (reports.isEmpty()) {
            return null;
        }
        else if (reports.size() == 1) {
            return reports.get(0);
        }
        // Error handling in case more than one reports exist for the exercise
        var latestReport = reports.stream().max(Comparator.comparing(DomainObject::getId)).get();
        var reportsToDelete = new ArrayList<>(reports);
        reportsToDelete.remove(latestReport);
        programmingExerciseGitDiffReportRepository.deleteAll(reportsToDelete);
        return latestReport;
    }

    /**
     * Obtain the {@link ProgrammingExerciseGitDiffReport} of a programming exercise.
     * If no report exists yet, it will try to generate one
     *
     * @param programmingExercise The programming exercise
     * @return The report or null if none can be generated
     */
    public ProgrammingExerciseGitDiffReport getOrCreateReportOfExercise(ProgrammingExercise programmingExercise) {
        var report = this.getReportOfExercise(programmingExercise);
        if (report == null) {
            return updateReport(programmingExercise);
        }
        else {
            return report;
        }
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport for a submission with the template repository.
     *
     * @param exercise   The exercise for which the report should be created
     * @param submission The submission for which the report should be created
     * @return The report with the changes between the submission and the template
     * @throws GitAPIException If an error occurs while accessing the git repository
     * @throws IOException     If an error occurs while accessing the file system
     */
    public ProgrammingExerciseGitDiffReport createReportForSubmissionWithTemplate(ProgrammingExercise exercise, ProgrammingSubmission submission)
            throws GitAPIException, IOException {
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        Repository templateRepo = prepareTemplateRepository(templateParticipation);

        var repo1 = gitService.checkoutRepositoryAtCommit(((ProgrammingExerciseParticipation) submission.getParticipation()).getVcsRepositoryUrl(), submission.getCommitHash(),
                false);
        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(repo1);
        var report = createReport(templateRepo, oldTreeParser, newTreeParser);
        gitService.switchBackToDefaultBranchHead(repo1);
        return report;
    }

    /**
     * Calculates git diff between two repositories and returns the cumulative number of diff lines.
     *
     * @param urlRepoA       url of the first repo to compare
     * @param localPathRepoA local path to the checked out instance of the first repo to compare
     * @param urlRepoB       url of the second repo to compare
     * @param localPathRepoB local path to the checked out instance of the second repo to compare
     * @return cumulative number of lines in the git diff of given repositories
     */
    public int calculateNumberOfDiffLinesBetweenRepos(VcsRepositoryUrl urlRepoA, Path localPathRepoA, VcsRepositoryUrl urlRepoB, Path localPathRepoB) {
        var repoA = gitService.getExistingCheckedOutRepositoryByLocalPath(localPathRepoA, urlRepoA);
        var repoB = gitService.getExistingCheckedOutRepositoryByLocalPath(localPathRepoB, urlRepoB);

        var treeParserRepoA = new FileTreeIterator(repoA);
        var treeParserRepoB = new FileTreeIterator(repoB);

        try (var diffOutputStream = new ByteArrayOutputStream(); var git = Git.wrap(repoB)) {
            git.diff().setOldTree(treeParserRepoB).setNewTree(treeParserRepoA).setOutputStream(diffOutputStream).call();
            var diff = diffOutputStream.toString();
            return extractDiffEntries(diff, true).stream().mapToInt(ProgrammingExerciseGitDiffEntry::getLineCount).sum();
        }
        catch (IOException | GitAPIException e) {
            log.error("Error calculating number of diff lines between repositories: urlRepoA={}, urlRepoB={}.", urlRepoA, urlRepoB, e);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport for an exercise.
     * It will take the git-diff between the template and solution repositories and return all changes.
     *
     * @param templateParticipation The participation for the template
     * @param solutionParticipation The participation for the solution
     * @return The changes between template and solution
     * @throws GitAPIException If there was an issue with JGit
     */
    private ProgrammingExerciseGitDiffReport generateReport(TemplateProgrammingExerciseParticipation templateParticipation,
            SolutionProgrammingExerciseParticipation solutionParticipation) throws GitAPIException, IOException {
        Repository templateRepo = prepareTemplateRepository(templateParticipation);
        var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUrl(), true);
        gitService.resetToOriginHead(solutionRepo);
        gitService.pullIgnoreConflicts(solutionRepo);

        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(solutionRepo);

        return createReport(templateRepo, oldTreeParser, newTreeParser);
    }

    /**
     * Prepares the template repository for the git diff calculation by checking it out and resetting it to the origin head.
     *
     * @param templateParticipation The participation for the template
     * @return The checked out template repository
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    private Repository prepareTemplateRepository(TemplateProgrammingExerciseParticipation templateParticipation) throws GitAPIException {
        var templateRepo = gitService.getOrCheckoutRepository(templateParticipation.getVcsRepositoryUrl(), true);
        gitService.resetToOriginHead(templateRepo);
        gitService.pullIgnoreConflicts(templateRepo);
        return templateRepo;
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff for two submissions.
     *
     * @param submission1 The first submission (older)
     * @param submission2 The second submission (newer)
     * @return The report with the changes between the two submissions
     * @throws GitAPIException If an error occurs while accessing the git repository
     * @throws IOException     If an error occurs while accessing the file system
     */
    public ProgrammingExerciseGitDiffReport generateReportForSubmissions(ProgrammingSubmission submission1, ProgrammingSubmission submission2) throws GitAPIException, IOException {
        var repositoryUrl = ((ProgrammingExerciseParticipation) submission1.getParticipation()).getVcsRepositoryUrl();
        var repo1 = gitService.getOrCheckoutRepository(repositoryUrl, true);
        var repo1Path = repo1.getLocalPath();
        var repo2Path = fileService.getTemporaryUniqueSubfolderPath(repo1Path.getParent(), 5);
        FileSystemUtils.copyRecursively(repo1Path, repo2Path);
        repo1 = gitService.checkoutRepositoryAtCommit(repo1, submission1.getCommitHash());
        var repo2 = gitService.getExistingCheckedOutRepositoryByLocalPath(repo2Path, repositoryUrl);
        repo2 = gitService.checkoutRepositoryAtCommit(repo2, submission2.getCommitHash());
        return parseFilesAndCreateReport(repo1, repo2);
    }

    /**
     * Parses the files of the given repositories and creates a new ProgrammingExerciseGitDiffReport containing the git-diff.
     *
     * @param repo1 The first repository
     * @param repo2 The second repository
     * @return The report with the changes between the two repositories at their checked out state
     * @throws IOException     If an error occurs while accessing the file system
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    @NotNull
    private ProgrammingExerciseGitDiffReport parseFilesAndCreateReport(Repository repo1, Repository repo2) throws IOException, GitAPIException {
        var oldTreeParser = new FileTreeIterator(repo1);
        var newTreeParser = new FileTreeIterator(repo2);

        var report = createReport(repo1, oldTreeParser, newTreeParser);
        gitService.switchBackToDefaultBranchHead(repo1);
        gitService.switchBackToDefaultBranchHead(repo2);
        return report;
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff.
     * <p>
     * It parses all files of the repositories in their directories on the file system and creates a report containing the changes.
     * Both repositories have to be checked out at the commit that should be compared and be in different directories
     *
     * @param repo1         The first repository
     * @param oldTreeParser The tree parser for the first repository
     * @param newTreeParser The tree parser for the second repository
     * @return The report with the changes between the two repositories at their checked out state
     * @throws IOException     If an error occurs while accessing the file system
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    @NotNull
    private ProgrammingExerciseGitDiffReport createReport(Repository repo1, FileTreeIterator oldTreeParser, FileTreeIterator newTreeParser) throws IOException, GitAPIException {
        try (ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream(); Git git = Git.wrap(repo1)) {
            git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setOutputStream(diffOutputStream).call();
            var diff = diffOutputStream.toString();
            var programmingExerciseGitDiffEntries = extractDiffEntries(diff, false);
            var report = new ProgrammingExerciseGitDiffReport();
            for (ProgrammingExerciseGitDiffEntry gitDiffEntry : programmingExerciseGitDiffEntries) {
                gitDiffEntry.setGitDiffReport(report);
            }
            report.setEntries(new HashSet<>(programmingExerciseGitDiffEntries));
            return report;
        }
    }

    /**
     * Extracts the ProgrammingExerciseGitDiffEntry from the raw git-diff output
     *
     * @param diff The raw git-diff output
     * @return The extracted ProgrammingExerciseGitDiffEntries
     */
    private List<ProgrammingExerciseGitDiffEntry> extractDiffEntries(String diff, boolean useAbsoluteLineCount) {
        var lines = diff.split("\n");
        var parserState = new ParserState();

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            // Filter out no new line message
            if ("\\ No newline at end of file".equals(line)) {
                continue;
            }
            var lineMatcher = gitDiffLinePattern.matcher(line);
            if (lineMatcher.matches()) {
                handleNewDiffBlock(lines, i, parserState, lineMatcher);
            }
            else if (!parserState.deactivateCodeReading) {
                switch (line.charAt(0)) {
                    case '+' -> handleAddition(parserState);
                    case '-' -> handleRemoval(parserState, useAbsoluteLineCount);
                    case ' ' -> handleUnchanged(parserState);
                    default -> parserState.deactivateCodeReading = true;
                }
            }
        }
        if (!parserState.currentEntry.isEmpty()) {
            parserState.entries.add(parserState.currentEntry);
        }
        return parserState.entries;
    }

    private void handleNewDiffBlock(String[] lines, int currentLine, ParserState parserState, Matcher lineMatcher) {
        if (!parserState.currentEntry.isEmpty()) {
            parserState.entries.add(parserState.currentEntry);
        }
        // Start of a new file
        var newFilePath = getFilePath(lines, currentLine);
        var newPreviousFilePath = getPreviousFilePath(lines, currentLine);
        if (newFilePath != null || newPreviousFilePath != null) {
            parserState.currentFilePath = newFilePath;
            parserState.currentPreviousFilePath = newPreviousFilePath;
        }
        parserState.currentEntry = new ProgrammingExerciseGitDiffEntry();
        parserState.currentEntry.setFilePath(parserState.currentFilePath);
        parserState.currentEntry.setPreviousFilePath(parserState.currentPreviousFilePath);
        parserState.currentLineCount = Integer.parseInt(lineMatcher.group("newLine"));
        parserState.currentPreviousLineCount = Integer.parseInt(lineMatcher.group("previousLine"));
        parserState.deactivateCodeReading = false;
    }

    private void handleUnchanged(ParserState parserState) {
        var entry = parserState.currentEntry;
        if (!entry.isEmpty()) {
            parserState.entries.add(entry);
        }
        entry = new ProgrammingExerciseGitDiffEntry();
        entry.setFilePath(parserState.currentFilePath);
        entry.setPreviousFilePath(parserState.currentPreviousFilePath);

        parserState.currentEntry = entry;
        parserState.lastLineRemoveOperation = false;
        parserState.currentLineCount++;
        parserState.currentPreviousLineCount++;
    }

    private void handleRemoval(ParserState parserState, boolean useAbsoluteLineCount) {
        var entry = parserState.currentEntry;
        if (!parserState.lastLineRemoveOperation && !entry.isEmpty()) {
            parserState.entries.add(entry);
            entry = new ProgrammingExerciseGitDiffEntry();
            entry.setFilePath(parserState.currentFilePath);
            entry.setPreviousFilePath(parserState.currentPreviousFilePath);
        }
        if (entry.getPreviousLineCount() == null) {
            entry.setPreviousLineCount(0);
            entry.setPreviousStartLine(parserState.currentPreviousLineCount);
        }
        if (useAbsoluteLineCount) {
            if (parserState.currentEntry.getLineCount() == null) {
                parserState.currentEntry.setLineCount(0);
                parserState.currentEntry.setStartLine(parserState.currentLineCount);
            }
            parserState.currentEntry.setLineCount(parserState.currentEntry.getLineCount() + 1);
        }
        else {
            entry.setPreviousLineCount(entry.getPreviousLineCount() + 1);
        }

        parserState.currentEntry = entry;
        parserState.lastLineRemoveOperation = true;
        parserState.currentPreviousLineCount++;
    }

    private void handleAddition(ParserState parserState) {
        if (parserState.currentEntry.getLineCount() == null) {
            parserState.currentEntry.setLineCount(0);
            parserState.currentEntry.setStartLine(parserState.currentLineCount);
        }
        parserState.currentEntry.setLineCount(parserState.currentEntry.getLineCount() + 1);

        parserState.lastLineRemoveOperation = false;
        parserState.currentLineCount++;
    }

    /**
     * Extracts the file path from the raw git-diff for a specified diff block
     *
     * @param lines       All lines of the raw git-diff
     * @param currentLine The line where the gitDiffLinePattern matched
     * @return The file path of the current diff block
     */
    private String getFilePath(String[] lines, int currentLine) {
        if (currentLine > 1 && lines[currentLine - 1].startsWith("+++ ") && lines[currentLine - 2].startsWith("--- ")) {
            var filePath = lines[currentLine - 1].substring(4);
            // Check if the filePath is /dev/null (which means the file was deleted) and instead return null
            if (DiffEntry.DEV_NULL.equals(filePath)) {
                return null;
            }
            // Git diff usually puts the two repos into the subfolders 'a' and 'b' for comparison, which we filter out here
            if (filePath.startsWith("a/") || filePath.startsWith("b/")) {
                return filePath.substring(2);
            }
        }
        return null;
    }

    /**
     * Extracts the previous file path from the raw git-diff for a specified diff block
     *
     * @param lines       All lines of the raw git-diff
     * @param currentLine The line where the gitDiffLinePattern matched
     * @return The previous file path of the current diff block
     */
    private String getPreviousFilePath(String[] lines, int currentLine) {
        if (currentLine > 1 && lines[currentLine - 1].startsWith("+++ ") && lines[currentLine - 2].startsWith("--- ")) {
            var filePath = lines[currentLine - 2].substring(4);
            // Check if the filePath is /dev/null (which means the file was deleted) and instead return null
            if (DiffEntry.DEV_NULL.equals(filePath)) {
                return null;
            }
            // Git diff usually puts the two repos into the subfolders 'a' and 'b' for comparison, which we filter out here
            if (filePath.startsWith("a/") || filePath.startsWith("b/")) {
                return filePath.substring(2);
            }
        }
        return null;
    }

    private boolean canUseExistingReport(ProgrammingExerciseGitDiffReport report, String templateHash, String solutionHash) {
        return report.getTemplateRepositoryCommitHash().equals(templateHash) && report.getSolutionRepositoryCommitHash().equals(solutionHash);
    }

    /**
     * Helper class for parsing the raw git-diff
     */
    private static class ParserState {

        private final List<ProgrammingExerciseGitDiffEntry> entries;

        private String currentFilePath;

        private String currentPreviousFilePath;

        private ProgrammingExerciseGitDiffEntry currentEntry;

        private boolean deactivateCodeReading;

        private boolean lastLineRemoveOperation;

        private int currentLineCount;

        private int currentPreviousLineCount;

        public ParserState() {
            entries = new ArrayList<>();
            currentEntry = new ProgrammingExerciseGitDiffEntry();
            deactivateCodeReading = true;
            lastLineRemoveOperation = false;
            currentLineCount = 0;
            currentPreviousLineCount = 0;
        }
    }
}
