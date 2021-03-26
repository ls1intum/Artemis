package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "grade_step")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradeStep extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    private GradingScale gradingScale;

    @Column(name = "lower_bound_percentage")
    private double lowerBoundPercentage;

    @Column(name = "lower_bound_inclusive")
    private boolean lowerBoundInclusive = true; // default

    @Column(name = "upper_bound_percentage")
    private double upperBoundPercentage;

    @Column(name = "upper_bound_inclusive")
    private boolean upperBoundInclusive = false; // default

    @Column(name = "grade_name")
    private String gradeName;

    @Column(name = "is_passing_grade")
    private boolean isPassingGrade;

    public GradingScale getGradingScale() {
        return gradingScale;
    }

    public void setGradingScale(GradingScale gradingScale) {
        this.gradingScale = gradingScale;
    }

    public double getLowerBoundPercentage() {
        return lowerBoundPercentage;
    }

    public void setLowerBoundPercentage(double lowerBoundPercentage) {
        this.lowerBoundPercentage = lowerBoundPercentage;
    }

    public boolean isLowerBoundInclusive() {
        return lowerBoundInclusive;
    }

    public void setLowerBoundInclusive(boolean lowerBoundInclusive) {
        this.lowerBoundInclusive = lowerBoundInclusive;
    }

    public double getUpperBoundPercentage() {
        return upperBoundPercentage;
    }

    public void setUpperBoundPercentage(double upperBoundPercentage) {
        this.upperBoundPercentage = upperBoundPercentage;
    }

    public boolean isUpperBoundInclusive() {
        return upperBoundInclusive;
    }

    public void setUpperBoundInclusive(boolean upperBoundInclusive) {
        this.upperBoundInclusive = upperBoundInclusive;
    }

    public String getGradeName() {
        return gradeName;
    }

    public void setGradeName(String gradeName) {
        this.gradeName = gradeName;
    }

    public boolean isPassingGrade() {
        return isPassingGrade;
    }

    public void setPassingGrade(boolean passingGrade) {
        isPassingGrade = passingGrade;
    }

    @Override
    public String toString() {
        return "GradeStep{" + "lowerBoundPercentage=" + lowerBoundPercentage + ", lowerBoundInclusive=" + lowerBoundInclusive + ", upperBoundPercentage=" + upperBoundPercentage
                + ", upperBoundInclusive=" + upperBoundInclusive + ", gradeName='" + gradeName + '\'' + ", isPassingGrade=" + isPassingGrade + '}';
    }
}
