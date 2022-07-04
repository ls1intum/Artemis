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

    // The ProgrammingSubmission to which this CoverageReport is related. This ProgrammingSubmission is always related
    // to a SolutionProgrammingExerciseParticipation because the report will only be generated for solution participations.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", referencedColumnName = "id")
    @JsonIgnore
    private ProgrammingSubmission submission;

    // When retrieving only the aggregated data (such as covered line ratio), the file reports are not required
    @OneToMany(mappedBy = "fullReport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "fullReport" }, allowSetters = true)
    private Set<CoverageFileReport> fileReports;

    // The ratio between the number of lines that are covered by all tests and the number of lines for all files in
    // the corresponding (solution) submission. The attribute can take values within the range of [0, 1].
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
