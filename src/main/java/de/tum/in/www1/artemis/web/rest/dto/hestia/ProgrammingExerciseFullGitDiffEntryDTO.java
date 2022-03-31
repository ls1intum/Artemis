package de.tum.in.www1.artemis.web.rest.dto.hestia;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single difference from a git-diff report between the template and solution repositories.
 * This is the full version of the git-diff entry that does contain the actual code of the change.
 * It was created from a ProgrammingExerciseGitDiffEntry.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseFullGitDiffEntryDTO {

    private String previousFilePath;

    private String filePath;

    private Integer previousLine;

    private Integer line;

    private String previousCode;

    private String code;

    public String getPreviousFilePath() {
        return previousFilePath;
    }

    public void setPreviousFilePath(String previousFilePath) {
        this.previousFilePath = previousFilePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getPreviousLine() {
        return previousLine;
    }

    public void setPreviousLine(Integer previousLine) {
        this.previousLine = previousLine;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public String getPreviousCode() {
        return previousCode;
    }

    public void setPreviousCode(String previousCode) {
        this.previousCode = previousCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return code == null && previousCode == null;
    }
}
