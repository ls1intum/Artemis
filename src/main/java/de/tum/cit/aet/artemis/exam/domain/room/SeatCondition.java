package de.tum.cit.aet.artemis.exam.domain.room;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Enum representing the conditions of a seat, i.e., if it's usable, broken, wheelchair-accessible, etc.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum SeatCondition {

    USABLE, NO_TABLE, DEFECT, WHEELCHAIR;

    /**
     * Converts a flag, as stored in the JSON input files, or as stored in the DB, to the respective enum instance
     *
     * @param flag The flag
     * @return The enum instance that was converted from the given flag
     * @throws IllegalArgumentException If the flag was not recognized
     */
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
