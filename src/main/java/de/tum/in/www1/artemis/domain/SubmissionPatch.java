package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmissionPatch;
import de.tum.in.www1.artemis.domain.participation.Participation;

/**
 * A SubmissionPatch is a DTO that is used to update a submission.
 * It contains the participation and the patch that should be applied to the submission,
 * in the format of a JSON patch (RFC 6902).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
// Annotation necessary to distinguish between concrete implementations of Submission when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = ModelingSubmissionPatch.class, name = "modeling"),
})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class SubmissionPatch extends DomainObject {

    /**
     * The participation that the submission belongs to.
     */
    private Participation participation;

    /**
     * The patch that should be applied to the submission, in the format of a JSON patch (RFC 6902).
     */
    public JsonNode patch;

    public abstract String getSubmissionExerciseType();
}
