package de.tum.cit.aet.artemis.communication.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;

/**
 * A bookmark of a post, holding only what a reader of the bookmark list needs.
 * <p>
 * This is the value of a distributed cache, which is why it is a record of scalars rather than the entity. The entity
 * carries a reference to its {@code User}, and a {@code User} reaches most of the domain model, so caching it would put
 * the password hash and the push notification secrets of every bookmarking user into the store, and would make the
 * stored shape change whenever an unrelated entity is refactored.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SavedPostDTO(long postId, PostingType postType, SavedPostStatus status) implements Serializable {
}
