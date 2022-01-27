package de.tum.in.www1.artemis.domain.hestia;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * A ProgrammingExerciseSolutionEntry.
 */
@Entity
@Table(name = "programming_exercise_solution_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseSolutionEntry extends DomainObject {

    @Column(name = "file")
    private String file;

    @Column(name = "previous_line")
    private Integer previousLine;

    @Column(name = "line")
    private Integer line;

    @Column(name = "previous_code")
    private String previousCode;

    @Column(name = "code")
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("solutionEntries")
    private CodeHint codeHint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("solutionEntries")
    private ProgrammingExerciseTestCase testCase;

    public String getFile() {
        return file;
    }

    public ProgrammingExerciseSolutionEntry file(String file) {
        setFile(file);
        return this;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public Integer getPreviousLine() {
        return previousLine;
    }

    public ProgrammingExerciseSolutionEntry previousLine(Integer previousLine) {
        setPreviousLine(previousLine);
        return this;
    }

    public void setPreviousLine(Integer previousLine) {
        this.previousLine = previousLine;
    }

    public Integer getLine() {
        return line;
    }

    public ProgrammingExerciseSolutionEntry line(Integer line) {
        setLine(line);
        return this;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public String getPreviousCode() {
        return previousCode;
    }

    public ProgrammingExerciseSolutionEntry previousCode(String previousCode) {
        setPreviousCode(previousCode);
        return this;
    }

    public void setPreviousCode(String previousCode) {
        this.previousCode = previousCode;
    }

    public String getCode() {
        return code;
    }

    public ProgrammingExerciseSolutionEntry code(String code) {
        setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public CodeHint getCodeHint() {
        return codeHint;
    }

    public ProgrammingExerciseSolutionEntry codeHint(CodeHint codeHint) {
        setCodeHint(codeHint);
        return this;
    }

    public void setCodeHint(CodeHint codeHint) {
        this.codeHint = codeHint;
    }

    public ProgrammingExerciseTestCase getTestCase() {
        return this.testCase;
    }

    public ProgrammingExerciseSolutionEntry testCase(ProgrammingExerciseTestCase testCase) {
        setTestCase(testCase);
        return this;
    }

    public void setTestCase(ProgrammingExerciseTestCase testCase) {
        this.testCase = testCase;
    }
}
