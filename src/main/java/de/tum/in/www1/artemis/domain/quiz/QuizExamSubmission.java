package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.exam.StudentExam;

@Entity
@DiscriminatorValue("QE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizExamSubmission extends AbstractQuizSubmission {

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "student_exam_id")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private StudentExam studentExam;

    public StudentExam getStudentExam() {
        return studentExam;
    }

    public void setStudentExam(StudentExam studentExam) {
        this.studentExam = studentExam;
    }

    @Override
    public String toString() {
        return "QuizExamSubmission{" + "id=" + getId() + ", scoreInPoints='" + getScoreInPoints() + "'" + "}";
    }
}
