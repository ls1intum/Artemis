package de.tum.in.www1.artemis.domain.exam.monitoring;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.*;

/**
 * Defines an action performed by a student during an exam.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
// @formatter:off
@JsonSubTypes({@JsonSubTypes.Type(value = ConnectionUpdatedAction.class, name = "CONNECTION_UPDATED"),
               @JsonSubTypes.Type(value = StartedExamAction.class, name = "STARTED_EXAM"),
               @JsonSubTypes.Type(value = SwitchedExerciseAction.class, name = "SWITCHED_EXERCISE"),
               @JsonSubTypes.Type(value = SavedExerciseAction.class, name = "SAVED_EXERCISE"),
               @JsonSubTypes.Type(value = HandedInEarlyAction.class, name = "HANDED_IN_EARLY"),
               @JsonSubTypes.Type(value = ContinuedAfterHandedInEarlyAction.class, name = "CONTINUED_AFTER_HAND_IN_EARLY"),
               @JsonSubTypes.Type(value = EndedExamAction.class, name = "ENDED_EXAM")})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamAction extends DomainObject {

    /**
     * In order to avoid DTOs, we use this value to create and identify the correct exam activity.
     * This value is used for this purpose only. There is no guarantee that this value is always correct in other cases.
     */
    protected Long studentExamId;

    /**
     * In order to avoid DTOs, we use this value to identify the correct exam activity.
     * This value is used for this purpose only. There is no guarantee that this value is always correct in other cases.
     */
    protected Long examActivityId;

    /**
     * Define the time when this action was performed.
     */
    protected ZonedDateTime timestamp;

    /**
     * Defines the type of the performed action (necessary to avoid DTOs)
     */
    protected ExamActionType type;

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public ExamActionType getType() {
        return type;
    }

    public void setType(ExamActionType type) {
        this.type = type;
    }

    public Long getStudentExamId() {
        return studentExamId;
    }

    public void setStudentExamId(Long studentExamId) {
        this.studentExamId = studentExamId;
    }

    public Long getExamActivityId() {
        return examActivityId;
    }

    public void setExamActivityId(Long examActivityId) {
        this.examActivityId = examActivityId;
    }
}
