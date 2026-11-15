package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of committing changed security policies: the policies and the Security Framework state as the tests repository now has them.
 *
 * @param editor the policies, read from the new commit
 * @param config the Security Framework state after the commit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyCommitResultDTO(SecurityPolicyEditorDTO editor, SecurityFrameworkConfigDTO config) {
}
