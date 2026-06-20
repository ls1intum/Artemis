package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO returned after creating or fetching a gocast course binding.
 * <p>
 * Combines the persisted {@link GocastBindingDTO} with the approval link that the instructor must visit
 * to grant the Artemis service account course-admin access on the gocast side.
 * <p>
 * {@code approvalUrl} is {@code null} for non-{@code PENDING} bindings (i.e. {@code ACTIVE} or
 * {@code REVOKED}); the {@code @JsonInclude(NON_EMPTY)} annotation suppresses it from the JSON response
 * in that case, which is intentional — the client must not try to re-open an approval URL when the
 * binding is already {@code ACTIVE} or {@code REVOKED}.
 *
 * @param binding     the binding (may be any status)
 * @param approvalUrl the gocast approval-page URL (only present for {@code PENDING} bindings; {@code null} otherwise)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastBindingWithApprovalDTO(GocastBindingDTO binding, String approvalUrl) {
}
