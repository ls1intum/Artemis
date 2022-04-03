package de.tum.in.www1.artemis.domain.hestia;

import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

/**
 * A testwise coverage report representing the executed code by file path of a single ProgrammingExerciseTestCase.
 * The entries contain the information about executed code by the start line and the length (i.e. number of lines) of
 * a consecutively executed block.
 */
@Entity
@Table(name = "coverage_report")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CoverageReport extends DomainObject {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", referencedColumnName = "id")
    @JsonIgnore
    private ProgrammingSubmission submission;

    // when retrieving only the aggregated data (such as covered line ratio), we do not need the file reports
    @OneToMany(mappedBy = "fullReport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("fullReport")
    private Set<CoverageFileReport> fileReports;

    @Column(name = "covered_line_ratio")
    private Double coveredLineRatio;

    public ProgrammingSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(ProgrammingSubmission submission) {
        this.submission = submission;
    }

    public Set<CoverageFileReport> getFileReports() {
        return fileReports;
    }

    public void setFileReports(Set<CoverageFileReport> fileReports) {
        this.fileReports = fileReports;
    }

    public Double getCoveredLineRatio() {
        return coveredLineRatio;
    }

    public void setCoveredLineRatio(Double coveredLineRatio) {
        this.coveredLineRatio = coveredLineRatio;
    }
}
