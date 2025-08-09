package de.tum.cit.aet.artemis.exam.domain.room;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Enum representing the conditions of a seat, i.e., if it's usable, broken, wheelchair-accessible, etc.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum SeatCondition {

    USABLE, NO_TABLE, DEFECT, WHEELCHAIR;

    public static SeatCondition seatConditionFromFlag(String flag) {
        if (flag == null) {
            throw new IllegalArgumentException("Seat condition flag can not be null");
        }

        return switch (flag.strip().toUpperCase(Locale.ROOT)) {
            case "", "USABLE" -> USABLE;
            case "T", "NO_TABLE" -> NO_TABLE;
            case "D", "DEFECT" -> DEFECT;
            case "W", "WHEELCHAIR" -> WHEELCHAIR;
            default -> throw new IllegalArgumentException("Couldn't convert '" + flag + "' to a seat condition");
        };
    }
}
