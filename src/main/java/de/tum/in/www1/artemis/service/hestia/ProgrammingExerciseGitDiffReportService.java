package de.tum.in.www1.artemis.service.hestia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.hestia.ProgrammingExerciseFullGitDiffEntryDTO;
import de.tum.in.www1.artemis.web.rest.dto.hestia.ProgrammingExerciseFullGitDiffReportDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * The service handling ProgrammingExerciseGitDiffReport and their ProgrammingExerciseGitDiffEntries.
 */
@Service
public class ProgrammingExerciseGitDiffReportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportService.class);

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final Pattern gitDiffLinePattern = Pattern.compile("@@ -(?<previousLine>\\d+),(?<previousLineCount>\\d+) \\+(?<newLine>\\d+),(?<newLineCount>\\d+) @@");

    public ProgrammingExerciseGitDiffReportService(GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
    }

    /**
     * Gets the full git-diff report of a programming exercise. A full git-diff report is created from the normal git-diff report
     * but contains the actual code blocks of the template and solution.
     *
     * @param programmingExercise The programming exercise
     * @return The full git-diff report for the given programming exercise
     */
    public ProgrammingExerciseFullGitDiffReportDTO getFullReport(ProgrammingExercise programmingExercise) {
        try {
            var report = programmingExerciseGitDiffReportRepository.findByProgrammingExerciseId(programmingExercise.getId());
            if (report == null) {
                return null;
            }
            var templateParticipationOptional = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
            var solutionParticipationOptional = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
            if (templateParticipationOptional.isEmpty() || solutionParticipationOptional.isEmpty()) {
                return null;
            }
            var templateParticipation = templateParticipationOptional.get();
            var solutionParticipation = solutionParticipationOptional.get();
            var templateRepo = gitService.getOrCheckoutRepository(templateParticipation.getVcsRepositoryUrl(), true);
            var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUrl(), true);

            var fullReport = new ProgrammingExerciseFullGitDiffReportDTO();
            fullReport.setTemplateRepositoryCommitHash(report.getTemplateRepositoryCommitHash());
            fullReport.setSolutionRepositoryCommitHash(report.getSolutionRepositoryCommitHash());
            var templateFiles = repositoryService.getFilesWithContent(templateRepo);
            var solutionFiles = repositoryService.getFilesWithContent(solutionRepo);
            fullReport.setEntries(report.getEntries().stream().map(entry -> convertToFullEntry(entry, templateFiles, solutionFiles)).filter(fullEntry -> !fullEntry.isEmpty())
                    .collect(Collectors.toSet()));

            return fullReport;
        }
        catch (InterruptedException | GitAPIException e) {
            log.error("Exception while generating full git diff report", e);
            throw new InternalServerErrorException("Error while generating full git-diff: " + e.getMessage());
        }
    }

    /**
     * Converts a normal git-diff entry to a full git-diff entry containing the actual code block of the change it represents.
     *
     * @param entry The normal git-diff entry
     * @param templateRepoFiles The files of the solution repository
     * @param solutionRepoFiles The files of the template repository
     * @return The full git-diff entry
     */
    private ProgrammingExerciseFullGitDiffEntryDTO convertToFullEntry(ProgrammingExerciseGitDiffEntry entry, Map<String, String> templateRepoFiles,
            Map<String, String> solutionRepoFiles) {
        var fullEntry = new ProgrammingExerciseFullGitDiffEntryDTO();
        fullEntry.setLine(entry.getStartLine());
        fullEntry.setPreviousLine(entry.getPreviousStartLine());
        fullEntry.setFilePath(entry.getFilePath());
        fullEntry.setPreviousFilePath(entry.getPreviousFilePath());
        if (entry.getPreviousFilePath() != null && entry.getPreviousStartLine() != null && entry.getPreviousLineCount() != null) {
            var fileContent = templateRepoFiles.get(entry.getPreviousFilePath());
            if (fileContent != null) {
                var previousCode = Arrays.stream(fileContent.split("\n")).skip(entry.getPreviousStartLine() - 1).limit(entry.getPreviousLineCount())
                        .collect(Collectors.joining("\n"));
                fullEntry.setPreviousCode(previousCode);
            }
        }
        if (entry.getFilePath() != null && entry.getStartLine() != null && entry.getLineCount() != null) {
            var fileContent = solutionRepoFiles.get(entry.getFilePath());
            if (fileContent != null) {
                var code = Arrays.stream(fileContent.split("\n")).skip(entry.getStartLine() - 1).limit(entry.getLineCount()).collect(Collectors.joining("\n"));
                fullEntry.setCode(code);
            }
        }
        return fullEntry;
    }

    /**
     * Updates the ProgrammingExerciseGitDiffReport of a programming exercise.
     * If there were no changes since the last report was created this will not do anything.
     * If there were changes to at least one of the repositories a new report will be created.
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
        var existingReport = programmingExerciseGitDiffReportRepository.findByProgrammingExerciseId(programmingExercise.getId());
        if (existingReport != null && canUseExistingReport(existingReport, templateHash, solutionHash)) {
            return existingReport;
        }
        try {
            var newReport = generateReport(templateParticipation, solutionParticipation);
            newReport.setTemplateRepositoryCommitHash(templateHash);
            newReport.setSolutionRepositoryCommitHash(solutionHash);
            newReport.setProgrammingExercise(programmingExercise);
            // Delete the old report first
            programmingExerciseGitDiffReportRepository.deleteByProgrammingExerciseId(programmingExercise.getId());
            newReport = programmingExerciseGitDiffReportRepository.save(newReport);
            programmingExercise.setGitDiffReport(newReport);
            programmingExerciseRepository.save(programmingExercise);
            return newReport;
        }
        catch (InterruptedException | GitAPIException | IOException e) {
            log.error("Exception while generating git diff report", e);
            throw new InternalServerErrorException("Error while generating git-diff: " + e.getMessage());
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
            SolutionProgrammingExerciseParticipation solutionParticipation) throws GitAPIException, InterruptedException, IOException {
        var templateRepo = gitService.getOrCheckoutRepository(templateParticipation.getVcsRepositoryUrl(), true);
        var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUrl(), true);

        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(solutionRepo);

        try (ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream()) {
            Git git = Git.wrap(templateRepo);
            git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setOutputStream(diffOutputStream).call();
            var diff = diffOutputStream.toString();
            var programmingExerciseGitDiffEntries = extractDiffEntries(diff);
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
     * @return The extraced ProgrammingExerciseGitDiffEntries
     */
    private List<ProgrammingExerciseGitDiffEntry> extractDiffEntries(String diff) {
        var lines = diff.split("\n");
        var entries = new ArrayList<ProgrammingExerciseGitDiffEntry>();
        String currentFilePath = null;
        String currentPreviousFilePath = null;
        var currentEntry = new ProgrammingExerciseGitDiffEntry();
        boolean deactivateCodeReading = true;
        boolean lastLineRemoveOperation = false;
        int currentLineCount = 0;
        int currentPreviousLineCount = 0;

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            // Filter out no new line message
            if ("\\ No newline at end of file".equals(line)) {
                continue;
            }
            var lineMatcher = gitDiffLinePattern.matcher(line);
            if (lineMatcher.matches()) {
                if (!currentEntry.isEmpty()) {
                    entries.add(currentEntry);
                }
                // Start of a new file
                var newFilePath = getFilePath(lines, i);
                var newPreviousFilePath = getPreviousFilePath(lines, i);
                if (newFilePath != null || newPreviousFilePath != null) {
                    currentFilePath = newFilePath;
                    currentPreviousFilePath = newPreviousFilePath;
                }
                currentEntry = new ProgrammingExerciseGitDiffEntry();
                currentEntry.setFilePath(currentFilePath);
                currentEntry.setPreviousFilePath(currentPreviousFilePath);
                currentLineCount = Integer.parseInt(lineMatcher.group("newLine"));
                currentPreviousLineCount = Integer.parseInt(lineMatcher.group("previousLine"));
                deactivateCodeReading = false;
            }
            else if (!deactivateCodeReading) {
                switch (line.charAt(0)) {
                    case '+' -> {
                        handleAddition(currentEntry, currentLineCount);

                        lastLineRemoveOperation = false;
                        currentLineCount++;
                    }
                    case '-' -> {
                        currentEntry = handleRemoval(entries, currentFilePath, currentPreviousFilePath, currentEntry, lastLineRemoveOperation, currentPreviousLineCount);

                        lastLineRemoveOperation = true;
                        currentPreviousLineCount++;
                    }
                    case ' ' -> {
                        currentEntry = handleUnchanged(entries, currentFilePath, currentPreviousFilePath, currentEntry);

                        lastLineRemoveOperation = false;
                        currentLineCount++;
                        currentPreviousLineCount++;
                    }
                    default -> {
                        deactivateCodeReading = true;
                    }
                }
            }
        }
        if (!currentEntry.isEmpty()) {
            entries.add(currentEntry);
        }
        return entries;
    }

    @NotNull
    private ProgrammingExerciseGitDiffEntry handleUnchanged(List<ProgrammingExerciseGitDiffEntry> entries, String currentFilePath, String currentPreviousFilePath,
            ProgrammingExerciseGitDiffEntry currentEntry) {
        var entry = currentEntry;
        if (!entry.isEmpty()) {
            entries.add(entry);
        }
        entry = new ProgrammingExerciseGitDiffEntry();
        entry.setFilePath(currentFilePath);
        entry.setPreviousFilePath(currentPreviousFilePath);
        return entry;
    }

    @NotNull
    private ProgrammingExerciseGitDiffEntry handleRemoval(List<ProgrammingExerciseGitDiffEntry> entries, String currentFilePath, String currentPreviousFilePath,
            ProgrammingExerciseGitDiffEntry currentEntry, boolean lastLineRemoveOperation, int currentPreviousLineCount) {
        var entry = currentEntry;
        if (!lastLineRemoveOperation && !entry.isEmpty()) {
            entries.add(entry);
            entry = new ProgrammingExerciseGitDiffEntry();
            entry.setFilePath(currentFilePath);
            entry.setPreviousFilePath(currentPreviousFilePath);
        }
        if (entry.getPreviousLineCount() == null) {
            entry.setPreviousLineCount(0);
            entry.setPreviousStartLine(currentPreviousLineCount);
        }
        entry.setPreviousLineCount(entry.getPreviousLineCount() + 1);
        return entry;
    }

    private void handleAddition(ProgrammingExerciseGitDiffEntry currentEntry, int currentLineCount) {
        if (currentEntry.getLineCount() == null) {
            currentEntry.setLineCount(0);
            currentEntry.setStartLine(currentLineCount);
        }
        currentEntry.setLineCount(currentEntry.getLineCount() + 1);
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
}
