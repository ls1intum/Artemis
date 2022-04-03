package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.*;

/**
 * A ProgrammingExerciseTestCase.
 */
@Entity
@Table(name = "programming_exercise_test_case")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestCase extends DomainObject {

    @Column(name = "test_name")
    private String testName;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "active")
    private Boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    private Visibility visibility;

    @Column(name = "bonus_multiplier")
    private Double bonusMultiplier;

    @Column(name = "bonus_points")
    private Double bonusPoints;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "programming_exercise_task_test_case", joinColumns = @JoinColumn(name = "test_case_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "task_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "testCases", "exercise" })
    private Set<ProgrammingExerciseTask> tasks = new HashSet<>();

    @OneToMany(mappedBy = "testCase", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("testCase")
    private Set<ProgrammingExerciseSolutionEntry> solutionEntries = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("testCases")
    private ProgrammingExercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_case_type")
    private ProgrammingExerciseTestCaseType type;

    @OneToMany(mappedBy = "testCase", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("testCase")
    private Set<TestwiseCoverageReportEntry> coverageEntries;

    public ProgrammingExerciseTestCase id(Long id) {
        setId(id);
        return this;
    }

    public String getTestName() {
        return testName;
    }

    public ProgrammingExerciseTestCase testName(String testName) {
        this.testName = testName;
        return this;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Double getWeight() {
        return weight;
    }

    public ProgrammingExerciseTestCase weight(Double weight) {
        this.weight = weight;
        return this;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    @Nonnull
    public Double getBonusMultiplier() {
        return bonusMultiplier != null ? bonusMultiplier : 1.0;
    }

    public ProgrammingExerciseTestCase bonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
        return this;
    }

    public void setBonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
    }

    @Nonnull
    public Double getBonusPoints() {
        return bonusPoints != null ? bonusPoints : 0.0;
    }

    public ProgrammingExerciseTestCase bonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
        return this;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public Set<ProgrammingExerciseTask> getTasks() {
        return tasks;
    }

    public void setTasks(Set<ProgrammingExerciseTask> tasks) {
        this.tasks = tasks;
    }

    public Set<ProgrammingExerciseSolutionEntry> getSolutionEntries() {
        return solutionEntries;
    }

    public void setSolutionEntries(Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
    }

    public Boolean isActive() {
        return active;
    }

    public ProgrammingExerciseTestCase active(Boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ProgrammingExerciseTestCase exercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    @JsonIgnore
    public boolean isAfterDueDate() {
        return visibility == Visibility.AFTER_DUE_DATE;
    }

    @JsonIgnore
    public boolean isInvisible() {
        return visibility == Visibility.NEVER;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public ProgrammingExerciseTestCase visibility(Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public ProgrammingExerciseTestCaseType getType() {
        return type;
    }

    public void setType(ProgrammingExerciseTestCaseType programmingExerciseTestCaseType) {
        this.type = programmingExerciseTestCaseType;
    }

    public Set<TestwiseCoverageReportEntry> getCoverageEntries() {
        return coverageEntries;
    }

    public void setCoverageEntries(Set<TestwiseCoverageReportEntry> coverageEntries) {
        this.coverageEntries = coverageEntries;
    }

    /**
     * Needs to be checked and updated if there is a new class attribute. Creates a clone with all attributes set to the value of the object, including the id.
     *
     * @return a clone of the object.
     */
    public ProgrammingExerciseTestCase clone() {
        ProgrammingExerciseTestCase clone = new ProgrammingExerciseTestCase().testName(this.getTestName()).weight(this.getWeight()).active(this.isActive())
                .bonusPoints(this.getBonusPoints()).bonusMultiplier(this.getBonusMultiplier()).visibility(visibility).exercise(this.exercise);
        clone.setId(this.getId());
        return clone;
    }

    /**
     * Checks for logical equality based on the name and the exercise
     * @param testCase another test case which should be checked for being the same
     * @return whether this and the other test case are the same based on name and exercise
     */
    public boolean isSameTestCase(ProgrammingExerciseTestCase testCase) {
        return testCase.getTestName().equalsIgnoreCase(this.getTestName()) && this.getExercise().getId().equals(testCase.getExercise().getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTestCase{" + "id=" + getId() + ", testName='" + testName + '\'' + ", weight=" + weight + ", active=" + active + ", visibility=" + visibility
                + ", bonusMultiplier=" + bonusMultiplier + ", bonusPoints=" + bonusPoints + '}';
    }
}
