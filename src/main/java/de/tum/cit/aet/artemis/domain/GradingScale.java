package de.tum.cit.aet.artemis.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.exam.Exam;

/**
 * A grading scale for a course or an exam that consists of grade steps
 */
@Entity
@Table(name = "grading_scale")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradingScale extends DomainObject {

    /**
     * "U" stands for "Unterschleif"
     */
    public static final String DEFAULT_PLAGIARISM_GRADE = "U";  // This should be the same as the corresponding constant in grading-scale.model.ts

    public static final String DEFAULT_NO_PARTICIPATION_GRADE = "X";  // This should be the same as the corresponding constant in grading-scale.model.ts

    private static final int MAX_SPECIAL_GRADE_SIZE = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_type")
    private GradeType gradeType = GradeType.NONE; // default

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_strategy")
    private BonusStrategy bonusStrategy;

    @Size(max = MAX_SPECIAL_GRADE_SIZE)
    @Column(name = "plagiarism_grade")
    private String plagiarismGrade;

    @Size(max = MAX_SPECIAL_GRADE_SIZE)
    @Column(name = "no_participation_grade")
    private String noParticipationGrade;

    @OneToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @OneToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Nullable
    @Column(name = "presentations_number")
    private Integer presentationsNumber;

    @Nullable
    @Column(name = "presentations_weight")
    private Double presentationsWeight;

    /**
     * Current implementation works with one Bonus instance as GradingScale.bonusFrom per Bonus.bonusTo instance (OneToOne) but
     * the relation is defined as OneToMany in order to allow applying multiple bonuses.
     */
    @OneToMany(mappedBy = "gradingScale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties(value = "gradingScale", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<GradeStep> gradeSteps = new HashSet<>();

    @OneToMany(mappedBy = "bonusToGradingScale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "bonusFrom", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Bonus> bonusFrom = new HashSet<>();

    public GradeType getGradeType() {
        return gradeType;
    }

    public void setGradeType(GradeType gradeType) {
        this.gradeType = gradeType;
    }

    public BonusStrategy getBonusStrategy() {
        return bonusStrategy;
    }

    public void setBonusStrategy(BonusStrategy bonusStrategy) {
        this.bonusStrategy = bonusStrategy;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public Integer getPresentationsNumber() {
        return presentationsNumber;
    }

    public void setPresentationsNumber(Integer presentationsNumber) {
        this.presentationsNumber = presentationsNumber;
    }

    public Double getPresentationsWeight() {
        return presentationsWeight;
    }

    public void setPresentationsWeight(Double presentationsWeight) {
        this.presentationsWeight = presentationsWeight;
    }

    public Set<GradeStep> getGradeSteps() {
        return gradeSteps;
    }

    public void setGradeSteps(Set<GradeStep> gradeSteps) {
        this.gradeSteps = gradeSteps;
    }

    public Set<Bonus> getBonusFrom() {
        return bonusFrom;
    }

    public void setBonusFrom(Set<Bonus> bonusFrom) {
        this.bonusFrom = bonusFrom;
    }

    public void addBonusFrom(Bonus bonusFrom) {
        this.bonusFrom.add(bonusFrom);
        bonusFrom.setBonusToGradingScale(this);
    }

    /**
     * Gets the max points of the given grading scale from the related course or exam
     *
     * @return max points defined in the exam or course related to this grading scale
     */
    @JsonIgnore
    public int getMaxPoints() {
        if (this.getCourse() != null) {
            Integer maxPoints = this.getCourse().getMaxPoints();
            return maxPoints != null ? maxPoints : 0;
        }
        else {
            return this.getExam().getExamMaxPoints();
        }
    }

    /**
     * Gets the title for the given grading scale from the related course or exam
     *
     * @return title of the exam or course related to this grading scale
     */
    @JsonIgnore
    public String getTitle() {
        if (this.getCourse() != null) {
            return this.getCourse().getTitle();
        }
        else {
            return this.getExam().getTitle();
        }
    }

    /**
     * Gets the course of the grading scale either via the exam or directly.
     *
     * @return a Course related to this grading scale
     */
    @JsonIgnore
    public Course getCourseViaExamOrDirectly() {
        return this.getExam() != null ? this.getExam().getCourse() : this.getCourse();
    }

    /**
     * Returns the max grade from grade step set of the grading scale
     *
     * @return the max grade step
     */
    GradeStep maxGrade() {
        return getGradeSteps().stream().filter(gradeStep -> gradeStep.isUpperBoundInclusive() && gradeStep.getUpperBoundPercentage() == 100.0).findAny().orElse(null);
    }

    public String getPlagiarismGrade() {
        return plagiarismGrade;
    }

    public void setPlagiarismGrade(String plagiarismGrade) {
        this.plagiarismGrade = plagiarismGrade;
    }

    @JsonIgnore
    @NotNull
    public String getPlagiarismGradeOrDefault() {
        return Objects.requireNonNullElse(plagiarismGrade, DEFAULT_PLAGIARISM_GRADE);
    }

    public String getNoParticipationGrade() {
        return noParticipationGrade;
    }

    public void setNoParticipationGrade(String noParticipationGrade) {
        this.noParticipationGrade = noParticipationGrade;
    }

    @JsonIgnore
    @NotNull
    public String getNoParticipationGradeOrDefault() {
        return Objects.requireNonNullElse(noParticipationGrade, DEFAULT_NO_PARTICIPATION_GRADE);
    }

    @Override
    public String toString() {
        return "GradingScale{" + "gradeType=" + gradeType + ", bonusStrategy=" + bonusStrategy + '}';
    }
}
