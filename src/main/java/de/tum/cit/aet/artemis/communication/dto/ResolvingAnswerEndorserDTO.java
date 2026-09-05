package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Who marked an answer post as resolving its post: the endorsement Course Memory derives an entry's trust
 * tier from. A projection rather than a navigation from the entity, because {@code AnswerPost#resolvedBy} is
 * lazy and not part of the eager thread fetch, so reading it off a detached answer would fail.
 *
 * @param answerPostId  the id of the resolving answer post
 * @param endorserLogin the login of the user who marked it resolving
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResolvingAnswerEndorserDTO(Long answerPostId, String endorserLogin) {
}
