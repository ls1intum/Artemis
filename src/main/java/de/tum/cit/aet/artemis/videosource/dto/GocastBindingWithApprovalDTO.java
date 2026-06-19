package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO returned after creating a gocast course binding.
 * <p>
 * Combines the persisted {@link GocastBindingDTO} with the approval link that the instructor must visit
 * to grant the Artemis service account course-admin access on the gocast side.
 *
 * @param binding     the newly created binding (status will be {@code PENDING})
 * @param approvalUrl the gocast approval-page URL that the instructor must visit to authorize the binding
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastBindingWithApprovalDTO(GocastBindingDTO binding, String approvalUrl) {
}
