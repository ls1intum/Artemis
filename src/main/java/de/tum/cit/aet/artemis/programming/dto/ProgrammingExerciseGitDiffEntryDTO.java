package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffEntry;

/**
 * DTO for a git diff report entry.
 *
 * @param previousFilePath  the previous file path of the entry
 * @param filePath          the new file path of the entry
 * @param previousStartLine the previous start line of the entry
 * @param startLine         the new start line of the entry
 * @param previousLineCount the previous line count of the entry
 * @param lineCount         the new line count of the entry
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseGitDiffEntryDTO(String previousFilePath, String filePath, Integer previousStartLine, Integer startLine, Integer previousLineCount,
        Integer lineCount) {

    public ProgrammingExerciseGitDiffEntryDTO(ProgrammingExerciseGitDiffEntry entry) {
        this(entry.getPreviousFilePath(), entry.getFilePath(), entry.getPreviousStartLine(), entry.getStartLine(), entry.getPreviousLineCount(), entry.getLineCount());
    }
}
