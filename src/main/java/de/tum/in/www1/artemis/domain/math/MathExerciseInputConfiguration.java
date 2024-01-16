package de.tum.in.www1.artemis.domain.math;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MathExerciseExpressionInputConfiguration.class, name = "expression"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class MathExerciseInputConfiguration {
}
