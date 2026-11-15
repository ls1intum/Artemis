package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(requiredProperties = { "callerRole", "callCount" })
public record FeatureUsageRoleShareDTO(@Schema(implementation = String.class, allowableValues = {
        "SUPER_ADMIN", "ADMIN", "INSTRUCTOR", "EDITOR", "TEACHING_ASSISTANT", "STUDENT", "ANONYMOUS" }) Role callerRole, long callCount){
}
