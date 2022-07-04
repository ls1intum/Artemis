package de.tum.in.www1.artemis.domain.hestia;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * A single entry from testwise coverage report by file path and consecutive executed code block.
 * A block is represented by the start line and the length (i.e. number of lines) of the block.
 */
@Entity
@Table(name = "testwise_coverage_report_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TestwiseCoverageReportEntry extends DomainObject {

    @ManyToOne
    @JsonIgnoreProperties("testwiseCoverageEntries")
    private CoverageFileReport fileReport;

    @ManyToOne
    @JsonIgnoreProperties("coverageEntries")
    private ProgrammingExerciseTestCase testCase;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "line_count")
    private Integer lineCount;

    public CoverageFileReport getFileReport() {
        return fileReport;
    }

    public void setFileReport(CoverageFileReport fileReport) {
        this.fileReport = fileReport;
    }

    public ProgrammingExerciseTestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(ProgrammingExerciseTestCase testCase) {
        this.testCase = testCase;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }
}
