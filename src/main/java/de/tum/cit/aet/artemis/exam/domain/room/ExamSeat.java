package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

// TODO: @Conditional(ExamEnabled.class) ? (Also relevant for other tables)
@Entity
@Table(name = "exam_seat")
public class ExamSeat extends DomainObject {

    /**
     * The name of the seat
     */
    @Column(name = "label", nullable = false)
    private String label; // For example, "A1", "4", or "{row},{column}"

    @Column(name = "seat_condition", nullable = false)
    private SeatCondition seatCondition = SeatCondition.USABLE;

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private ExamRoom room;

    // Optional: Store visual layout information
    @Column(name = "x_position", nullable = false)
    private int x;

    @Column(name = "y_position", nullable = false)
    private int y;

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

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
