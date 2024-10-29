package de.tum.cit.aet.artemis.programming.domain.hestia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A single difference from a git-diff report between the template and solution repositories.
 * This is a light version of the changes that does not contain the actual code.
 * It rather contains the startLine and the length of the code blocks from both repositories.
 */
@Entity
@Table(name = "programming_exercise_git_diff_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseGitDiffEntry extends DomainObject {

    @ManyToOne
    @JsonIgnoreProperties("entries")
    private ProgrammingExerciseGitDiffReport gitDiffReport;

    @Column(name = "previous_file_path")
    private String previousFilePath;

    @Column(name = "file_path")
    private String filePath;

    // The line at which the previous code segment is in the template
    @Column(name = "previous_line")
    private Integer previousStartLine;

    // The line at which the new code segment is in the solution
    @Column(name = "line")
    private Integer startLine;

    // The amount of lines of the code segment in the template
    @Column(name = "previous_line_count")
    private Integer previousLineCount;

    // The amount of lines of the code segment in the solution
    @Column(name = "line_count")
    private Integer lineCount;

    public ProgrammingExerciseGitDiffReport getGitDiffReport() {
        return gitDiffReport;
    }

    public void setGitDiffReport(ProgrammingExerciseGitDiffReport gitDiff) {
        this.gitDiffReport = gitDiff;
    }

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

    public Integer getPreviousStartLine() {
        return previousStartLine;
    }

    public void setPreviousStartLine(Integer previousStartLine) {
        this.previousStartLine = previousStartLine;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getPreviousLineCount() {
        return previousLineCount;
    }

    public void setPreviousLineCount(Integer previousLineCount) {
        this.previousLineCount = previousLineCount;
    }

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }

    public boolean isEmpty() {
        return startLine == null && previousStartLine == null;
    }
}
