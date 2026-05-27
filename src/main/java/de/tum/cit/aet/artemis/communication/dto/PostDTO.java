package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * Internal carrier passed between Artemis services for post-related events.
 * <p>
 * Used by Iris (Pyris pipeline, autonomous tutor) and by the broadcast plumbing in
 * {@code PostingService}. It is never serialized to the wire: the websocket frame that actually
 * crosses the network is a separate {@link PostBroadcastDTO} whose payload is the cycle-free
 * {@link PostResponseDTO}; that mapping happens inside {@code PostingService.broadcastForPost} so
 * internal callers can keep working with the {@link Post} entity. As a purely in-process carrier it
 * carries no Jackson serialization annotations.
 *
 * @param post   the post entity in question
 * @param action which CRUD action this event describes
 */
public record PostDTO(Post post, MetisCrudAction action) {
}
