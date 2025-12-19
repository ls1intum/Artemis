package de.tum.cit.aet.artemis.communication.dto.exercise_review;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "contentType")
@JsonSubTypes({ @JsonSubTypes.Type(value = UserCommentContentDTO.class, name = "USER"),
        @JsonSubTypes.Type(value = ConsistencyIssueCommentContentDTO.class, name = "CONSISTENCY_CHECK") })
public sealed interface CommentContentDTO permits UserCommentContentDTO, ConsistencyIssueCommentContentDTO {
}
