package de.tum.cit.aet.artemis.exam.dto.room;

import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;

@Conditional(ExamEnabled.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamSeatDTO {

    /**
     * The name of the seat. This is usually a combination of the row and column name.
     */
    private String label; // For example, "A1", "4", or "{row},{column}"

    /**
     * The condition of the seat.
     */
    private SeatCondition seatCondition = SeatCondition.USABLE;

    /**
     * The x-coordinate of the seat in the respective exam room.
     */
    private float x;

    /**
     * The y-coordinate of the seat in the respective exam room
     */
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

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    @JsonIgnore
    public void setX(double x) {
        this.x = (float) x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    @JsonIgnore
    public void setY(double y) {
        this.y = (float) y;
    }
    /* Getters & Setters End */
}
