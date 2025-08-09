package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSeatDTO(
        /**
         * The name of the seat. This is usually a combination of the row and column name.
         */
        String label, // For example, "A1", "4", or "{row},{column}"

        /**
         * The condition of the seat.
         */
        SeatCondition seatCondition,

        /**
         * The x-coordinate of the seat in the respective exam room. x >= 0.
         */
        float x,

        /**
         * The y-coordinate of the seat in the respective exam room. y >= 0.
         */
        float y) {
}
