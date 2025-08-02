package de.tum.cit.aet.artemis.exam.domain.room;

import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

/**
 * Enum representing the conditions of a seat, i.e., if it's usable, broken, wheelchair-accessible, etc.
 */
@Conditional(ExamEnabled.class)
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum SeatCondition {

    USABLE, NO_TABLE, DEFECT, WHEELCHAIR;

    public static SeatCondition SeatConditionFromFlag(String flag) {
        return switch (flag.toUpperCase()) {
            case "", "USABLE" -> USABLE;
            case "T", "NO_TABLE" -> NO_TABLE;
            case "D", "DEFECT" -> DEFECT;
            case "W", "WHEELCHAIR" -> WHEELCHAIR;
            case null, default -> throw new IllegalArgumentException("Couldn't convert '" + flag + "' to a seat condition");
        };
    }
}
