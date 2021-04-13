package de.tum.in.www1.artemis.domain;

import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.exam.Exam;

/**
 * A grading scale for a course or an exam the consists of grade steps
 */
@Entity
@Table(name = "grading_scale")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradingScale extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_type")
    private GradeType gradeType = GradeType.NONE; // default

    @OneToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties("gradingScale")
    @JsonIgnore
    private Course course;

    @OneToOne
    @JoinColumn(name = "exam_id")
    @JsonIgnoreProperties("gradingScale")
    @JsonIgnore
    private Exam exam;

    @OneToMany(mappedBy = "gradingScale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("gradingScale")
    private Set<GradeStep> gradeSteps;

    public GradeType getGradeType() {
        return gradeType;
    }

    public void setGradeType(GradeType gradeType) {
        this.gradeType = gradeType;
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
}
