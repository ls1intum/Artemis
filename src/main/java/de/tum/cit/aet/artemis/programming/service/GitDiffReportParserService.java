package de.tum.cit.aet.artemis.programming.service;

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

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseGitDiffEntry;

@Profile(PROFILE_CORE)
@Service
public class GitDiffReportParserService {

    private static final String PREFIX_RENAME_FROM = "rename from ";

    private static final String PREFIX_RENAME_TO = "rename to ";

    private final Pattern gitDiffLinePattern = Pattern.compile("@@ -(?<previousLine>\\d+)(,(?<previousLineCount>\\d+))? \\+(?<newLine>\\d+)(,(?<newLineCount>\\d+))? @@.*");

    /**
     * Extracts the ProgrammingExerciseGitDiffEntry from the raw git-diff output
     *
     * @param diff                 The raw git-diff output
     * @param useAbsoluteLineCount Whether to use absolute line count or previous line count
     * @param ignoreWhitespace     Whether to ignore entries where only leading and trailing whitespace differ
     * @return The extracted ProgrammingExerciseGitDiffEntries
     */
    public List<ProgrammingExerciseGitDiffEntry> extractDiffEntries(String diff, boolean useAbsoluteLineCount, boolean ignoreWhitespace) {
        var lines = diff.split("\n");
        var parserState = new ParserState();
        Map<String, String> renamedFilePaths = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            // Filter out no new line message
            if ("\\ No newline at end of file".equals(line)) {
                continue;
            }

            // Check for renamed files
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
                handleNewDiffBlock(lines, i, parserState, lineMatcher, ignoreWhitespace);
            }
            else if (!parserState.deactivateCodeReading && !line.isEmpty()) {
                switch (line.charAt(0)) {
                    case '+' -> handleAddition(parserState, line);
                    case '-' -> handleRemoval(parserState, useAbsoluteLineCount, line);
                    case ' ' -> handleUnchanged(parserState, ignoreWhitespace);
                    default -> parserState.deactivateCodeReading = true;
                }
            }
        }

        // Check the last entry
        finalizeEntry(parserState, ignoreWhitespace);

        // Add empty entries for renamed files without changes
        for (var entry : renamedFilePaths.entrySet()) {
            var diffEntry = new ProgrammingExerciseGitDiffEntry();
            diffEntry.setFilePath(entry.getValue());
            diffEntry.setPreviousFilePath(entry.getKey());
            parserState.entries.add(diffEntry);
        }

        return parserState.entries;
    }

    private void handleNewDiffBlock(String[] lines, int currentLine, ParserState parserState, Matcher lineMatcher, boolean ignoreWhitespace) {
        finalizeEntry(parserState, ignoreWhitespace);

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
        parserState.addedLines.clear();
        parserState.removedLines.clear();
    }

    private void handleUnchanged(ParserState parserState, boolean ignoreWhitespace) {
        finalizeEntry(parserState, ignoreWhitespace);
        parserState.currentEntry = new ProgrammingExerciseGitDiffEntry();
        parserState.currentEntry.setFilePath(parserState.currentFilePath);
        parserState.currentEntry.setPreviousFilePath(parserState.currentPreviousFilePath);

        parserState.lastLineRemoveOperation = false;
        parserState.currentLineCount++;
        parserState.currentPreviousLineCount++;
        parserState.addedLines.clear();
        parserState.removedLines.clear();
    }

    private void handleRemoval(ParserState parserState, boolean useAbsoluteLineCount, String line) {
        var entry = parserState.currentEntry;
        if (!parserState.lastLineRemoveOperation && !entry.isEmpty()) {
            finalizeEntry(parserState, false);
            parserState.currentEntry = new ProgrammingExerciseGitDiffEntry();
            parserState.currentEntry.setFilePath(parserState.currentFilePath);
            parserState.currentEntry.setPreviousFilePath(parserState.currentPreviousFilePath);
        }

        // Store removed line
        parserState.removedLines.add(line.substring(1));

        if (parserState.currentEntry.getPreviousLineCount() == null) {
            parserState.currentEntry.setPreviousLineCount(0);
            parserState.currentEntry.setPreviousStartLine(parserState.currentPreviousLineCount);
        }
        if (useAbsoluteLineCount) {
            if (parserState.currentEntry.getLineCount() == null) {
                parserState.currentEntry.setLineCount(0);
                parserState.currentEntry.setStartLine(parserState.currentLineCount);
            }
            parserState.currentEntry.setLineCount(parserState.currentEntry.getLineCount() + 1);
        }
        else {
            parserState.currentEntry.setPreviousLineCount(parserState.currentEntry.getPreviousLineCount() + 1);
        }

        parserState.lastLineRemoveOperation = true;
        parserState.currentPreviousLineCount++;
    }

    private void handleAddition(ParserState parserState, String line) {
        // Store added line
        parserState.addedLines.add(line.substring(1));

        if (parserState.currentEntry.getLineCount() == null) {
            parserState.currentEntry.setLineCount(0);
            parserState.currentEntry.setStartLine(parserState.currentLineCount);
        }
        parserState.currentEntry.setLineCount(parserState.currentEntry.getLineCount() + 1);

        parserState.lastLineRemoveOperation = false;
        parserState.currentLineCount++;
    }

    private void finalizeEntry(ParserState parserState, boolean ignoreWhitespace) {
        if (!parserState.currentEntry.isEmpty()) {
            if (!ignoreWhitespace || !isWhitespaceOnlyChange(parserState.addedLines, parserState.removedLines)) {
                parserState.entries.add(parserState.currentEntry);
            }
        }
    }

    private boolean isWhitespaceOnlyChange(List<String> addedLines, List<String> removedLines) {
        if (addedLines.size() != removedLines.size()) {
            return false; // Different number of lines changed, definitely not whitespace only
        }

        for (int i = 0; i < addedLines.size(); i++) {
            String added = addedLines.get(i).trim();
            String removed = removedLines.get(i).trim();
            if (!added.equals(removed)) {
                return false;
            }
        }
        return true;
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

        private final List<String> addedLines;

        private final List<String> removedLines;

        public ParserState() {
            entries = new ArrayList<>();
            currentEntry = new ProgrammingExerciseGitDiffEntry();
            deactivateCodeReading = true;
            lastLineRemoveOperation = false;
            currentLineCount = 0;
            currentPreviousLineCount = 0;
            addedLines = new ArrayList<>();
            removedLines = new ArrayList<>();
        }
    }
}
