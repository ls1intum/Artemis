package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * How many calls over the window came from callers of one role.
 * <p>
 * The role is the caller's highest <i>global</i> authority, so this says which audience a deployment's traffic comes from,
 * not in which capacity a particular call was made. Only REST features contribute: git and background features have no
 * caller in the security context and are recorded as anonymous.
 *
 * @param callerRole the caller role
 * @param callCount  calls from that role over the window
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageRoleShareDTO(Role callerRole, long callCount) {
}
