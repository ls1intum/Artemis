package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "grade_step")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradeStep extends DomainObject {

    @Column(name = "lower_bound")
    private int lowerBound;

    @Column(name = "upper_bound")
    private int upperBound;

    @Column(name = "grade_name")
    private String gradeName;

    @Column(name = "is_passing_grade")
    private boolean isPassingGrade;

    public int getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(int lowerBound) {
        this.lowerBound = lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(int upperBound) {
        this.upperBound = upperBound;
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
        return "GradeStep{" + "lowerBound=" + lowerBound + ", upperBound=" + upperBound + ", gradeName='" + gradeName + '\'' + ", isPassingGrade=" + isPassingGrade + '}';
    }
}
