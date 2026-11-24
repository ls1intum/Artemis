package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The policies and the Security Framework state of one commit of the tests repository, e.g. the result of committing changed security policies. Both are
 * read from the same commit, so a commit pushed in between cannot mix two states.
 *
 * @param editor the policies, read from the commit
 * @param config the Security Framework state of the commit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyCommitResultDTO(SecurityPolicyEditorDTO editor, SecurityFrameworkConfigDTO config) {
}
