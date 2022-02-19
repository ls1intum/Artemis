package de.tum.in.www1.artemis.domain.hestia;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * A single difference from a git-diff report
 */
@Entity
@Table(name = "programming_exercise_git_diff_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseGitDiffEntry extends DomainObject {

    @ManyToOne
    @JsonIgnoreProperties("entries")
    private ProgrammingExerciseGitDiffReport gitDiffReport;

    @Column(name = "file_path")
    private String filePath;

    // The line at which the previous code segment is in the template
    @Column(name = "previous_line")
    private Integer previousLine;

    // The line at which the new code segment is in the solution
    @Column(name = "line")
    private Integer line;

    // The previous code segment to be replaced by the new code segment
    @Column(name = "previous_code")
    private String previousCode;

    // The new code segment that replaces the old code segment
    @Column(name = "code")
    private String code;

    public ProgrammingExerciseGitDiffReport getGitDiffReport() {
        return gitDiffReport;
    }

    public void setGitDiffReport(ProgrammingExerciseGitDiffReport gitDiff) {
        this.gitDiffReport = gitDiff;
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

    public boolean isEmpty() {
        return code == null && previousCode == null;
    }
}
