package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmissionPatch;
import de.tum.in.www1.artemis.domain.participation.Participation;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
// Annotation necessary to distinguish between concrete implementations of Submission when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = ModelingSubmissionPatch.class, name = "modeling"),
})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class SubmissionPatch extends DomainObject implements Comparable<SubmissionPatch> {

    private Participation participation;
    public JsonNode patch;

    public abstract String getSubmissionExerciseType();

    @Override
    public int compareTo(SubmissionPatch o) {
        return this.getId().compareTo(o.getId());
    }
}
