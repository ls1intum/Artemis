package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "exam_seat")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamSeat extends DomainObject {

    /**
     * The name of the seat. This is usually a combination of the row and column name.
     */
    @Column(name = "label", nullable = false)
    private String label; // For example, "A1", "4", or "{row},{column}"

    /**
     * The condition of the seat.
     */
    @Column(name = "seat_condition", nullable = false)
    private SeatCondition seatCondition = SeatCondition.USABLE;

    /**
     * The exam room this seat is located in.
     */
    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    @JsonBackReference
    private ExamRoom room;

    /**
     * The x-coordinate of the seat in the respective exam room.
     */
    @Column(name = "x_position", nullable = false)
    private float x;

    /**
     * The y-coordinate of the seat in the respective exam room
     */
    @Column(name = "y_position", nullable = false)
    private float y;

    /* Getters & Setters */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public SeatCondition getSeatCondition() {
        return seatCondition;
    }

    public void setSeatCondition(SeatCondition seatCondition) {
        this.seatCondition = seatCondition;
    }

    public ExamRoom getRoom() {
        return room;
    }

    public void setRoom(ExamRoom room) {
        this.room = room;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setX(double x) {
        this.x = (float) x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setY(double y) {
        this.y = (float) y;
    }
    /* Getters & Setters End */
}
