package de.tum.in.www1.artemis.domain.exam.monitoring;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

/**
 * In order to extend the actions in the future, this ExamActivity serves as a container of actions performed per student.
 */
@Entity
@Table(name = "exam_activity")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamActivity extends DomainObject {

    /**
     * Since we want to avoid DTOs (and avoid sending the complete exam object),
     * we only keep the corresponding student exam id.
     */
    @Column(name = "student_exam_id")
    private Long studentExamId;

    @OneToOne
    @JoinColumn(name = "student_exam_id", insertable = false, updatable = false)
    @JsonIgnore
    private StudentExam studentExam;

    /**
     * A set of unique actions performed by the student during the exam.
     */
    @OneToMany(mappedBy = "examActivity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private final List<ExamAction> examActions = new ArrayList<>();

    public Long getStudentExamId() {
        return studentExamId;
    }

    public void setStudentExamId(Long studentExamId) {
        this.studentExamId = studentExamId;
    }

    public void addExamAction(ExamAction examAction) {
        this.examActions.add(examAction);
    }

    public List<ExamAction> getExamActions() {
        return examActions;
    }

    @Override
    public String toString() {
        return "ExamActivity{" + "studentExamId=" + studentExamId + '}';
    }
}
