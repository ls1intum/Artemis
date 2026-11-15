package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A policy file an instructor imports into the policy editor, to replace a policy with it as unsaved changes.
 *
 * @param policyPath the path of the policy the file replaces
 * @param content    the content of the imported file
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyImportDTO(String policyPath, String content) {
}
