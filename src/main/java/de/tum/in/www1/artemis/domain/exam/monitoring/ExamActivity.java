package de.tum.in.www1.artemis.domain.exam.monitoring;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "exam_activity")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamActivity extends DomainObject {

    @Column(name = "student_exam_id")
    private Long studentExamId;

    @OneToMany(mappedBy = "examActivity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private Set<ExamAction> examActions = new HashSet<>();

    public Long getStudentExamId() {
        return studentExamId;
    }

    public void setStudentExamId(Long studentExamId) {
        this.studentExamId = studentExamId;
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
        return "ExamActivity{" + "studentExam=" + studentExamId + '}';
    }
}
