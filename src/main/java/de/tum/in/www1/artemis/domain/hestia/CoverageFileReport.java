package de.tum.in.www1.artemis.domain.hestia;

import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "coverage_file_report")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CoverageFileReport extends DomainObject {

    @ManyToOne
    @JsonIgnoreProperties("fileReports")
    private CoverageReport fullReport;

    @Column(name = "file_path")
    private String filePath;

    // The number of lines in the whole file
    @Column(name = "line_count")
    private Integer lineCount;

    // The number of lines that are covered by test cases. The number refers to the unique count, meaning the same line
    // that is covered in multiple entries will only count as one line
    @Column(name = "covered_line_count")
    private Integer coveredLineCount;

    @OneToMany(mappedBy = "fileReport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "fileReport" }, allowSetters = true)
    private Set<TestwiseCoverageReportEntry> testwiseCoverageEntries;

    public CoverageReport getFullReport() {
        return fullReport;
    }

    public void setFullReport(CoverageReport fullReport) {
        this.fullReport = fullReport;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }

    public Integer getCoveredLineCount() {
        return coveredLineCount;
    }

    public void setCoveredLineCount(Integer coveredLineCount) {
        this.coveredLineCount = coveredLineCount;
    }

    public Set<TestwiseCoverageReportEntry> getTestwiseCoverageEntries() {
        return testwiseCoverageEntries;
    }

    public void setTestwiseCoverageEntries(Set<TestwiseCoverageReportEntry> testwiseCoverageEntries) {
        this.testwiseCoverageEntries = testwiseCoverageEntries;
    }
}
