package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * Internal carrier passed between Artemis services for post-related events.
 * <p>
 * Used by Iris (Pyris pipeline, autonomous tutor) and by the broadcast plumbing in
 * {@code PostingService}. The websocket frame that actually crosses the network is a separate
 * {@link PostBroadcastDTO} whose payload is the cycle-free {@link PostResponseDTO}; that mapping
 * happens inside {@code PostingService.broadcastForPost} so internal callers can keep working with
 * the {@link Post} entity. The {@code @JsonInclude} annotation is kept to satisfy the project-wide
 * DTO convention enforced by {@code AbstractModuleCodeStyleTest.testDTOImplementations}.
 *
 * @param post   the post entity in question
 * @param action which CRUD action this event describes
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostDTO(Post post, MetisCrudAction action) {
}
