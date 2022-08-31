package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.exam.Exam;

/**
 * A grading scale for a course or an exam that consists of grade steps
 */
@Entity
@Table(name = "grading_scale")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradingScale extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_type")
    private GradeType gradeType = GradeType.NONE; // default

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_strategy")
    private BonusStrategy bonusStrategy;

    @OneToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @OneToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

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

    public Set<GradeStep> getGradeSteps() {
        return gradeSteps;
    }

    public void setGradeSteps(Set<GradeStep> gradeSteps) {
        this.gradeSteps = gradeSteps;
    }

    public Set<Bonus> getBonusFrom() {
        return bonusFrom;
    }

    public void addBonusFrom(Bonus bonusFrom) {
        this.bonusFrom.add(bonusFrom);
        bonusFrom.setBonusToGradingScale(this);
    }

    public void addBonus(Bonus bonus) {
        this.bonusFrom.add(bonus);
    }

    @JsonIgnore
    public int getMaxPoints() {
        if (this.getCourse() != null) {
            return this.getCourse().getMaxPoints();
        }
        else {
            return this.getExam().getMaxPoints();
        }
    }

    /**
     * Returns the max grade from grade step set of the grading scale
     * @return the max grade step
     */
    GradeStep maxGrade() {
        return getGradeSteps().stream().filter(gradeStep -> gradeStep.isUpperBoundInclusive() && gradeStep.getUpperBoundPercentage() == 100.0).findAny().orElse(null);
    }

    /**
     * Columns for which we allow a pageable search. For example see {@see de.tum.in.www1.artemis.service.TextExerciseService#getAllOnPageWithSize(PageableSearchDTO, User)}}
     * method. This ensures, that we can't search in columns that don't exist, or we do not want to be searchable.
     */
    public enum GradingScaleSearchColumn {

        ID("id"), COURSE_TITLE("course.title"), EXAM_TITLE("exam.title");

        private final String mappedColumnName;

        GradingScaleSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }
}
