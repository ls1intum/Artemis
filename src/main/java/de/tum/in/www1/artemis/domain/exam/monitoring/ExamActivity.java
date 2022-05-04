package de.tum.in.www1.artemis.domain.exam.monitoring;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

@Entity
@Table(name = "exam_activity")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamActivity extends DomainObject {

    @OneToOne
    @JoinColumn(name = "student_exam_id")
    @JsonBackReference
    private StudentExam studentExam;

    @OneToMany(mappedBy = "examActivity", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private Set<ExamAction> examActions = new HashSet<>();

    public StudentExam getStudentExam() {
        return studentExam;
    }

    public void setStudentExam(StudentExam studentExam) {
        this.studentExam = studentExam;
    }

    public Set<ExamAction> getExamActions() {
        return examActions;
    }

    public void setExamActions(Set<ExamAction> examActions) {
        this.examActions = examActions;
    }

    public void addExamActions(List<ExamAction> examActions) {
        this.examActions.addAll(examActions);
    }

    public void addExamAction(ExamAction examAction) {
        this.examActions.add(examAction);
    }

    @Override
    public String toString() {
        return "ExamActivity{" + "studentExam=" + studentExam + '}';
    }
}
