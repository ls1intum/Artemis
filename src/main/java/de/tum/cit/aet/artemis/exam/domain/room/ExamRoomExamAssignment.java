package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "exam_room_assignment")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamRoomExamAssignment extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "exam_room_id", nullable = false)
    @JsonBackReference
    private ExamRoom examRoom;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    @JsonBackReference
    private Exam exam;

    /* Getters & Setters */
    public ExamRoom getExamRoom() {
        return examRoom;
    }

    public void setExamRoom(ExamRoom examRoom) {
        this.examRoom = examRoom;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }
    /* Getters & Setters End */
}
