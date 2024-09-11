package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.diff.DiffEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;

@Profile(PROFILE_CORE)
@Service
public class GitDiffReportParserService {

    private static final String PREFIX_RENAME_FROM = "rename from ";

    private static final String PREFIX_RENAME_TO = "rename to ";

    private final Pattern gitDiffLinePattern = Pattern.compile("@@ -(?<previousLine>\\d+)(,(?<previousLineCount>\\d+))? \\+(?<newLine>\\d+)(,(?<newLineCount>\\d+))? @@");

    /**
     * Extracts the ProgrammingExerciseGitDiffEntry from the raw git-diff output
     *
     * @param diff                 The raw git-diff output
     * @param useAbsoluteLineCount Whether to use absolute line count or previous line count
     * @return The extracted ProgrammingExerciseGitDiffEntries
     */
    public List<ProgrammingExerciseGitDiffEntry> extractDiffEntries(String diff, boolean useAbsoluteLineCount) {
        var lines = diff.split("\n");
        var parserState = new ParserState();
        Map<String, String> renamedFilePaths = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            // Filter out no new line message
            if ("\\ No newline at end of file".equals(line)) {
                continue;
            }

            // Files may be renamed without changes, in which case the lineMatcher will never match the entry
            // We store this information separately so it is not lost
            if (line.startsWith(PREFIX_RENAME_FROM) && i + 1 < lines.length) {
                var nextLine = lines[i + 1];
                if (nextLine.startsWith(PREFIX_RENAME_TO)) {
                    var previousFilePath = line.substring(PREFIX_RENAME_FROM.length());
                    var currentFilePath = nextLine.substring(PREFIX_RENAME_TO.length());
                    renamedFilePaths.put(previousFilePath, currentFilePath);
                }
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
        // Add an empty diff entry for renamed files without changes
        for (var entry : renamedFilePaths.entrySet()) {
            var diffEntry = new ProgrammingExerciseGitDiffEntry();
            diffEntry.setFilePath(entry.getValue());
            diffEntry.setPreviousFilePath(entry.getKey());
            parserState.entries.add(diffEntry);
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
