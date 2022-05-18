package de.tum.in.www1.artemis.domain.hestia;

import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * A ProgrammingExerciseSolutionEntry represents a single change in a file that a students has to make in order to pass the related test.
 * It is structured similarly to a git diff entry.
 * If it replaces existing code it will contain the previous code that it replaces otherwise previousCode will be null.
 * If it is only removing existing code the code attribute will be null.
 * If it encompasses the addition of an entire file, previousLine will be null.
 * If it deletes an entire file, line will be null.
 * previousLine and line will be different when there are other changes higher up in the file.
 *
 * Example:
 * A print statement gets changed:
 * SolutionEntry{
 *         filePath = "<...>"
 *     previousLine = 12
 *     previousCode = System.out.println("Tset");
 *             line = 12
 *             code = System.out.println("Test");
 * }
 */
@Entity
@Table(name = "programming_exercise_solution_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseSolutionEntry extends DomainObject {

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

    // Fetched lazily, as we never need the code hint when fetching solution entries
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private CodeHint codeHint;

    @ManyToOne
    @JsonIgnoreProperties("solutionEntries")
    private ProgrammingExerciseTestCase testCase;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String file) {
        this.filePath = file;
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

    public CodeHint getCodeHint() {
        return codeHint;
    }

    public void setCodeHint(CodeHint codeHint) {
        this.codeHint = codeHint;
    }

    public ProgrammingExerciseTestCase getTestCase() {
        return this.testCase;
    }

    public void setTestCase(ProgrammingExerciseTestCase testCase) {
        this.testCase = testCase;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        ProgrammingExerciseSolutionEntry that = (ProgrammingExerciseSolutionEntry) obj;
        return Objects.equals(filePath, that.filePath) && Objects.equals(previousLine, that.previousLine) && Objects.equals(line, that.line)
                && Objects.equals(previousCode, that.previousCode) && Objects.equals(code, that.code);
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseSolutionEntry{" + "id=" + getId() + '\'' + ", filePath='" + filePath + '\'' + ", previousLine=" + previousLine + ", line=" + line
                + ", previousCode='" + previousCode + '\'' + ", code='" + code + '\'' + '}';
    }
}
