package de.tum.in.www1.artemis.domain.hestia;

import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * A testwise coverage report representing the executed code by file path of a single ProgrammingExerciseTestCase.
 * The entries contain the information about executed code by the start line and the length (i.e. number of lines) of
 * a consecutively executed block.
 */
@Entity
@Table(name = "programming_exercise_testwise_coverage_report")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestwiseCoverageReport extends DomainObject {

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("testwiseCoverageReport")
    @JoinColumn(name = "programming_exercise_test_case_id", referencedColumnName = "id")
    private ProgrammingExerciseTestCase testCase;

    @OneToMany(mappedBy = "testwiseCoverageReport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("testwiseCoverageReport")
    private Set<ProgrammingExerciseTestwiseCoverageEntry> entries;

    public ProgrammingExerciseTestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(ProgrammingExerciseTestCase testCase) {
        this.testCase = testCase;
    }

    public Set<ProgrammingExerciseTestwiseCoverageEntry> getEntries() {
        return entries;
    }

    public void setEntries(Set<ProgrammingExerciseTestwiseCoverageEntry> entries) {
        this.entries = entries;
    }
}
