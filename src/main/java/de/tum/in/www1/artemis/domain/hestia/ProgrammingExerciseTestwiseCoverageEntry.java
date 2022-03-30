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
 * A single entry from testwise coverage report by file path and consecutive executed code block.
 * A block is represented by the start line and the length (i.e. number of lines) of the block.
 */
@Entity
@Table(name = "programming_exercise_testwise_coverage_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestwiseCoverageEntry extends DomainObject {

    @ManyToOne
    @JsonIgnoreProperties("entries")
    private ProgrammingExerciseTestwiseCoverageReport testwiseCoverageReport;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "line_count")
    private Integer lineCount;

    public ProgrammingExerciseTestwiseCoverageReport getTestwiseCoverageReport() {
        return testwiseCoverageReport;
    }

    public void setTestwiseCoverageReport(ProgrammingExerciseTestwiseCoverageReport testwiseCoverageReport) {
        this.testwiseCoverageReport = testwiseCoverageReport;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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
