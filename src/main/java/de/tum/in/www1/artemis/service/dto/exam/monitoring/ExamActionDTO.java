package de.tum.in.www1.artemis.service.dto.exam.monitoring;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = ConnectionUpdatedActionDTO.class, name = "CONNECTION_UPDATED"),
        @JsonSubTypes.Type(value = StartedExamActionDTO.class, name = "STARTED_EXAM"), @JsonSubTypes.Type(value = SwitchedExerciseActionDTO.class, name = "SWITCHED_EXERCISE"),
        @JsonSubTypes.Type(value = SavedExerciseActionDTO.class, name = "SAVED_EXERCISE"), @JsonSubTypes.Type(value = ExamActionDTO.class, name = "HANDED_IN_EARLY"),
        @JsonSubTypes.Type(value = ExamActionDTO.class, name = "CONTINUED_AFTER_HAND_IN_EARLY"), @JsonSubTypes.Type(value = ExamActionDTO.class, name = "ENDED_EXAM") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamActionDTO extends DomainObject {

    protected ZonedDateTime timestamp;

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
}
