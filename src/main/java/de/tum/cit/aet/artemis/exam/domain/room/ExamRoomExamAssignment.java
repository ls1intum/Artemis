package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "exam_room_exam_assignment")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamRoomExamAssignment extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_room_id", nullable = false)
    @JsonBackReference("examRoomExamAssignments_room")
    private ExamRoom examRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    @JsonBackReference("examRoomExamAssignments_exam")
    private Exam exam;

    /**
     * An alias, i.e. an alternative name, for a room.
     * As opposed to {@link ExamRoom#getAlternativeName}, this pseudonym is bound to the linked exam.
     * May be used to more easily distinguish between two rooms with otherwise similar names, e.g. "Hörsaal 1" vs "HS1".
     */
    @Column(name = "room_alias", nullable = true, length = 255)
    private String roomAlias;

    public @NonNull ExamRoom getExamRoom() {
        return examRoom;
    }

    public void setExamRoom(@NonNull ExamRoom examRoom) {
        this.examRoom = examRoom;
    }

    public @NonNull Exam getExam() {
        return exam;
    }

    public void setExam(@NonNull Exam exam) {
        this.exam = exam;
    }

    public @Nullable String getRoomAlias() {
        return roomAlias;
    }

    public void setRoomAlias(@Nullable String roomAlias) {
        this.roomAlias = roomAlias;
    }
}
