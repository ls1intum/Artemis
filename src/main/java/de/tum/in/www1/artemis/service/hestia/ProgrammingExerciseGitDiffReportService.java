package de.tum.in.www1.artemis.service.hestia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

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
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
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

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final Pattern gitDiffLinePattern = Pattern.compile("@@ -(?<previousLine>\\d+),(?<previousLineCount>\\d+) \\+(?<newLine>\\d+),(?<newLineCount>\\d+) @@");

    public ProgrammingExerciseGitDiffReportService(GitService gitService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository) {
        this.gitService = gitService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
    }

    /**
     * Updates the ProgrammingExerciseGitDiffReport of a programming exercise.
     * If there were no changes since the last report was created this will return the old report.
     * If there were changes to at least one of the repositories a new report will be created.
     *
     * @param programmingExercise The ProgrammingExercise
     * @return The ProgrammingExerciseGitDiffReport for the given ProgrammingExercise
     */
    public ProgrammingExerciseGitDiffReport updateReportForExercise(ProgrammingExercise programmingExercise) {
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
            programmingExercise.setGitDiffReport(newReport);
            newReport = programmingExerciseGitDiffReportRepository.save(newReport);
            if (existingReport != null) {
                programmingExerciseGitDiffReportRepository.delete(existingReport);
            }
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
                currentFilePath = newFilePath == null ? currentFilePath : newFilePath;
                currentEntry = new ProgrammingExerciseGitDiffEntry();
                currentEntry.setFilePath(currentFilePath);
                currentLineCount = Integer.parseInt(lineMatcher.group("newLine"));
                currentPreviousLineCount = Integer.parseInt(lineMatcher.group("previousLine"));
                deactivateCodeReading = false;
            }
            else if (!deactivateCodeReading) {
                switch (line.charAt(0)) {
                    case '+' -> {
                        handleAddition(currentEntry, currentLineCount, line);

                        lastLineRemoveOperation = false;
                        currentLineCount++;
                    }
                    case '-' -> {
                        currentEntry = handleRemoval(entries, currentFilePath, currentEntry, lastLineRemoveOperation, currentPreviousLineCount, line);

                        lastLineRemoveOperation = true;
                        currentPreviousLineCount++;
                    }
                    case ' ' -> {
                        currentEntry = handleUnchanged(entries, currentFilePath, currentEntry);

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
        removeTrailingLinebreaks(entries);
        return entries;
    }

    @NotNull
    private ProgrammingExerciseGitDiffEntry handleUnchanged(List<ProgrammingExerciseGitDiffEntry> entries, String currentFilePath, ProgrammingExerciseGitDiffEntry currentEntry) {
        var entry = currentEntry;
        if (!entry.isEmpty()) {
            entries.add(entry);
        }
        entry = new ProgrammingExerciseGitDiffEntry();
        entry.setFilePath(currentFilePath);
        return entry;
    }

    @NotNull
    private ProgrammingExerciseGitDiffEntry handleRemoval(List<ProgrammingExerciseGitDiffEntry> entries, String currentFilePath, ProgrammingExerciseGitDiffEntry currentEntry,
            boolean lastLineRemoveOperation, int currentPreviousLineCount, String line) {
        var entry = currentEntry;
        if (!lastLineRemoveOperation && !entry.isEmpty()) {
            entries.add(entry);
            entry = new ProgrammingExerciseGitDiffEntry();
            entry.setFilePath(currentFilePath);
        }
        if (entry.getPreviousCode() == null) {
            entry.setPreviousCode("");
            entry.setPreviousLine(currentPreviousLineCount);
        }
        var previousCode = entry.getPreviousCode();
        previousCode += line.substring(1) + "\n";
        entry.setPreviousCode(previousCode);
        return entry;
    }

    private void handleAddition(ProgrammingExerciseGitDiffEntry currentEntry, int currentLineCount, String line) {
        if (currentEntry.getCode() == null) {
            currentEntry.setCode("");
            currentEntry.setLine(currentLineCount);
        }
        var code = currentEntry.getCode();
        code += line.substring(1) + "\n";
        currentEntry.setCode(code);
    }

    /**
     * Extracts the file path from the raw git-diff for a specified diff block
     *
     * @param lines All lines of the raw git-diff
     * @param currentLine The line where the gitDiffLinePattern matched
     * @return The file path of the current diff block
     */
    private String getFilePath(String[] lines, int currentLine) {
        if (lines[currentLine - 1].startsWith("+++ ") && lines[currentLine - 2].startsWith("--- ")) {
            var filePath = lines[currentLine - 1].substring(4);
            // Check if the filePath is /dev/null (which means the file was deleted) and replace it by the actual file path
            if (DiffEntry.DEV_NULL.equals(filePath)) {
                filePath = lines[currentLine - 2].substring(4);
            }
            // Git diff usually puts the two repos into the subfolders 'a' and 'b' for comparison, which we filter out here
            if (filePath.startsWith("a/") || filePath.startsWith("b/")) {
                return filePath.substring(2);
            }
        }
        return null;
    }

    private void removeTrailingLinebreaks(List<ProgrammingExerciseGitDiffEntry> entries) {
        for (ProgrammingExerciseGitDiffEntry entry : entries) {
            if (entry.getCode() != null) {
                entry.setCode(entry.getCode().substring(0, entry.getCode().length() - 1));
            }
            if (entry.getPreviousCode() != null) {
                entry.setPreviousCode(entry.getPreviousCode().substring(0, entry.getPreviousCode().length() - 1));
            }
        }
    }

    private boolean canUseExistingReport(ProgrammingExerciseGitDiffReport report, String templateHash, String solutionHash) {
        return report.getTemplateRepositoryCommitHash().equals(templateHash) && report.getSolutionRepositoryCommitHash().equals(solutionHash);
    }
}
