package de.tum.cit.aet.artemis.exercise.dto.review;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Base type for review comment content. Discriminator is contentType.", oneOf = { UserCommentContentDTO.class,
        ConsistencyIssueCommentContentDTO.class }, discriminatorProperty = "contentType")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "contentType")
@JsonSubTypes({ @JsonSubTypes.Type(value = UserCommentContentDTO.class, name = "USER"),
        @JsonSubTypes.Type(value = ConsistencyIssueCommentContentDTO.class, name = "CONSISTENCY_CHECK") })
public sealed interface CommentContentDTO permits UserCommentContentDTO, ConsistencyIssueCommentContentDTO {
}
