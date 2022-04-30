package de.tum.in.www1.artemis.domain.exam.monitoring;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

@Entity
@Table(name = "exam_entity")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamActivity extends DomainObject {

    @OneToOne
    @JoinColumn(name = "student_exam_id")
    private StudentExam studentExam;

    public StudentExam getStudentExam() {
        return studentExam;
    }

    @Override
    public String toString() {
        return "ExamActivity{" + "studentExam=" + studentExam + '}';
    }
}
